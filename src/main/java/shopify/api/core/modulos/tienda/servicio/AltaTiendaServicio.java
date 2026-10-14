package shopify.api.core.modulos.tienda.servicio;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.seguridad.RolTienda;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.balance.servicio.BalanceServicio;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionVigente;
import shopify.api.core.modulos.suscripcion.servicio.SuscripcionServicio;
import shopify.api.core.modulos.tienda.dto.request.CrearTiendaRequest;
import shopify.api.core.modulos.tienda.dto.response.SuscripcionResumenResponse;
import shopify.api.core.modulos.tienda.dto.response.TiendaCreadaResponse;
import shopify.api.core.modulos.tienda.entidad.PersonalTiendaEntidad;
import shopify.api.core.modulos.tienda.entidad.RolEntidad;
import shopify.api.core.modulos.tienda.entidad.TiendaEntidad;
import shopify.api.core.modulos.tienda.mapeador.TiendaMapeador;
import shopify.api.core.modulos.tienda.modelo.ValidadorSubdominio;
import shopify.api.core.modulos.tienda.repositorio.PersonalTiendaRepositorio;
import shopify.api.core.modulos.tienda.repositorio.RolRepositorio;
import shopify.api.core.modulos.tienda.repositorio.TiendaRepositorio;

/** Flujo {@code alta-de-tienda.md}: tienda, personal propietario, balance, suscripción y auditoría en una transacción. */
@Service
@RequiredArgsConstructor
public class AltaTiendaServicio {

    private static final String MONEDA_POR_DEFECTO = "USD";

    private final TiendaRepositorio tiendaRepositorio;
    private final PersonalTiendaRepositorio personalRepositorio;
    private final RolRepositorio rolRepositorio;
    private final SuscripcionServicio suscripcionServicio;
    private final BalanceServicio balanceServicio;
    private final AuditoriaServicio auditoriaServicio;
    private final TiendaMapeador mapeador;

    /** Q-015: una prueba gratuita por usuario; en {@code false} vuelve a una por tienda. */
    @Value("${app.suscripcion.prueba-una-por-usuario:true}")
    private boolean pruebaUnaPorUsuario;

    /** TIE-001, TIE-002, TIE-003, TIE-007, SUS-001, SUS-016. */
    @Transactional
    public TiendaCreadaResponse crear(CrearTiendaRequest request) {
        UUID propietarioId = SesionActual.usuarioId();

        String subdominio = ValidadorSubdominio.validar(request.subdominio());
        if (tiendaRepositorio.existsBySubdominio(subdominio)) {
            throw ValidadorSubdominio.enUso();
        }

        // Se consulta antes de insertar la tienda para no contar la que se está creando (SUS-016)
        boolean pendienteDePago = pruebaUnaPorUsuario && suscripcionServicio.propietarioYaUsoPrueba(propietarioId);

        TiendaEntidad tienda = new TiendaEntidad();
        tienda.setPropietarioId(propietarioId);
        tienda.setNombre(request.nombre().trim());
        tienda.setSubdominio(subdominio);
        tienda.setCorreoContacto(request.correoContacto().trim().toLowerCase(Locale.ROOT));
        tienda.setMoneda(request.moneda() == null || request.moneda().isBlank() ? MONEDA_POR_DEFECTO : request.moneda());
        try {
            tienda = tiendaRepositorio.saveAndFlush(tienda);
        } catch (DataIntegrityViolationException ex) {
            // Carrera por el subdominio: UqTiendasSubdominio
            throw ValidadorSubdominio.enUso();
        }

        RolEntidad rolPropietario = rolRepositorio.findByCodigoAndEliminadoFalse(RolTienda.PROPIETARIO.name())
                .orElseThrow(() -> new IllegalStateException("Falta el rol PROPIETARIO en el catálogo de roles"));
        PersonalTiendaEntidad propietario = new PersonalTiendaEntidad();
        propietario.setTiendaId(tienda.getId());
        propietario.setUsuarioId(propietarioId);
        propietario.setRolId(rolPropietario.getId());
        personalRepositorio.save(propietario);

        balanceServicio.crearSiNoExiste(tienda.getId());

        SuscripcionVigente suscripcion = pendienteDePago
                ? suscripcionServicio.crearPendienteDePago(tienda.getId(), request.codigoPlan())
                : suscripcionServicio.crearPrueba(tienda.getId(), request.codigoPlan());

        auditoriaServicio.registrar(tienda.getId(), AccionAuditoria.TIENDA_CREADA, "Tiendas", tienda.getId(), null,
                Map.of("subdominio", subdominio,
                        "plan", suscripcion.codigoPlan(),
                        "estado_suscripcion", suscripcion.estado().name()));

        SuscripcionResumenResponse resumen = mapeador.aResumen(suscripcion);
        return mapeador.aCreada(tienda, resumen, suscripcion.permiteOperar());
    }
}
