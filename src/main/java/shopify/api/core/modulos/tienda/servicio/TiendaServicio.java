package shopify.api.core.modulos.tienda.servicio;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionVigente;
import shopify.api.core.modulos.suscripcion.servicio.SuscripcionServicio;
import shopify.api.core.modulos.tienda.dto.request.ActualizarTiendaRequest;
import shopify.api.core.modulos.tienda.dto.response.MiTiendaResponse;
import shopify.api.core.modulos.tienda.dto.response.SuscripcionResumenResponse;
import shopify.api.core.modulos.tienda.dto.response.TiendaConfiguracionResponse;
import shopify.api.core.modulos.tienda.dto.response.TiendaVitrinaResponse;
import shopify.api.core.modulos.tienda.entidad.TiendaEntidad;
import shopify.api.core.modulos.tienda.mapeador.TiendaMapeador;
import shopify.api.core.modulos.tienda.repositorio.TiendaRepositorio;

@Service
@RequiredArgsConstructor
public class TiendaServicio {

    private static final String MENSAJE_TIENDA_NO_DISPONIBLE = "Esta tienda no está disponible";

    private final TiendaRepositorio tiendaRepositorio;
    private final SuscripcionServicio suscripcionServicio;
    private final AuditoriaServicio auditoriaServicio;
    private final TiendaMapeador mapeador;

    /**
     * TIE-004: la tienda existe, no está eliminada, está activa y su suscripción permite operar.
     * Lo usan la vitrina, el catálogo y el checkout.
     */
    @Transactional(readOnly = true)
    public boolean esOperativa(UUID tiendaId) {
        return tiendaRepositorio.findByIdAndEliminadoFalse(tiendaId)
                .filter(tienda -> Boolean.TRUE.equals(tienda.getEstaActiva()))
                .map(tienda -> suscripcionOperativa(tiendaId))
                .orElse(false);
    }

    /** Tiendas del usuario en sesión, con su rol (propietario o personal). */
    @Transactional(readOnly = true)
    public List<MiTiendaResponse> misTiendas() {
        return tiendaRepositorio.listarDeUsuario(SesionActual.usuarioId()).stream()
                .map(vista -> mapeador.aMiTienda(vista,
                        Boolean.TRUE.equals(vista.getEstaActiva()) && suscripcionOperativa(vista.getTiendaId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TiendaConfiguracionResponse configuracion(ContextoTienda contexto) {
        return aConfiguracion(buscar(contexto));
    }

    /** TIE-005 y TIE-006. */
    @Transactional
    public TiendaConfiguracionResponse actualizarConfiguracion(ContextoTienda contexto, ActualizarTiendaRequest request) {
        TiendaEntidad tienda = buscar(contexto);

        if (!ZoneId.getAvailableZoneIds().contains(request.zonaHoraria().trim())) {
            throw new ReglaNegocioException("TIE-006", "La zona horaria no es válida");
        }
        if (!tienda.getMoneda().equals(request.moneda()) && tiendaRepositorio.tieneOrdenes(tienda.getId())) {
            throw new ConflictoEstadoException("TIE-005",
                    "No puedes cambiar la moneda porque la tienda ya tiene órdenes");
        }

        tienda.setNombre(request.nombre().trim());
        tienda.setCorreoContacto(request.correoContacto().trim().toLowerCase(Locale.ROOT));
        tienda.setDominioPersonalizado(request.dominioPersonalizado() == null || request.dominioPersonalizado().isBlank()
                ? null : request.dominioPersonalizado().trim());
        tienda.setMoneda(request.moneda());
        tienda.setTasaImpuesto(request.tasaImpuesto());
        tienda.setPreciosIncluyenImpuesto(request.preciosIncluyenImpuesto());
        tienda.setZonaHoraria(request.zonaHoraria().trim());
        return aConfiguracion(tiendaRepositorio.saveAndFlush(tienda));
    }

    /** TIE-008: solo el propietario activa la tienda; ya activa no cambia ni audita. */
    @Transactional
    public TiendaConfiguracionResponse activar(ContextoTienda contexto, String motivo) {
        return cambiarEstado(contexto, true, motivo, AccionAuditoria.TIENDA_ACTIVADA);
    }

    /** TIE-008: solo el propietario desactiva la tienda; el panel sigue accesible, la vitrina no. */
    @Transactional
    public TiendaConfiguracionResponse desactivar(ContextoTienda contexto, String motivo) {
        return cambiarEstado(contexto, false, motivo, AccionAuditoria.TIENDA_DESACTIVADA);
    }

    /** TIE-009: datos públicos; una tienda que no existe o no es operativa responde igual. */
    @Transactional(readOnly = true)
    public TiendaVitrinaResponse vitrina(String subdominio) {
        String clave = subdominio == null ? "" : subdominio.trim().toLowerCase(Locale.ROOT);
        return tiendaRepositorio.findBySubdominioAndEliminadoFalse(clave)
                .filter(tienda -> Boolean.TRUE.equals(tienda.getEstaActiva()) && suscripcionOperativa(tienda.getId()))
                .map(mapeador::aVitrina)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", MENSAJE_TIENDA_NO_DISPONIBLE));
    }

    private TiendaConfiguracionResponse cambiarEstado(ContextoTienda contexto, boolean activa, String motivo,
                                                      AccionAuditoria accion) {
        if (!contexto.esPropietario()) {
            throw new AccesoDenegadoException("SEG-002", "Tu rol no tiene permiso para esta acción");
        }
        TiendaEntidad tienda = buscar(contexto);
        if (Boolean.valueOf(activa).equals(tienda.getEstaActiva())) {
            return aConfiguracion(tienda);
        }

        tienda.setEstaActiva(activa);
        tiendaRepositorio.saveAndFlush(tienda);
        auditoriaServicio.registrar(tienda.getId(), accion, "Tiendas", tienda.getId(),
                motivo == null || motivo.isBlank() ? null : motivo.trim(),
                Map.of("antes", Map.of("esta_activa", !activa), "despues", Map.of("esta_activa", activa)));
        return aConfiguracion(tienda);
    }

    private TiendaEntidad buscar(ContextoTienda contexto) {
        return tiendaRepositorio.findByIdAndEliminadoFalse(contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("SEG-001", "No encontramos esa tienda"));
    }

    private TiendaConfiguracionResponse aConfiguracion(TiendaEntidad tienda) {
        Optional<SuscripcionVigente> vigente = suscripcionServicio.vigente(tienda.getId());
        boolean operativa = Boolean.TRUE.equals(tienda.getEstaActiva())
                && vigente.map(SuscripcionVigente::permiteOperar).orElse(false);
        SuscripcionResumenResponse resumen = vigente.map(mapeador::aResumen).orElse(null);
        return mapeador.aConfiguracion(tienda, operativa, resumen);
    }

    private boolean suscripcionOperativa(UUID tiendaId) {
        return suscripcionServicio.vigente(tiendaId)
                .map(SuscripcionVigente::permiteOperar)
                .orElse(false);
    }
}
