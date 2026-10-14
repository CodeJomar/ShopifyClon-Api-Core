package shopify.api.core.modulos.suscripcion.servicio;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.modulos.suscripcion.dto.response.SuscripcionPlataformaResponse;
import shopify.api.core.modulos.suscripcion.entidad.SuscripcionTiendaEntidad;
import shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion;
import shopify.api.core.modulos.suscripcion.repositorio.SuscripcionTiendaRepositorio;

/** Listado y suspensión de tiendas desde la plataforma (SUS-010). */
@Service
@RequiredArgsConstructor
public class PlataformaSuscripcionServicio {

    static final int TAMANO_PAGINA = 20;

    private static final String ENTIDAD = "SuscripcionesTienda";

    private final SuscripcionTiendaRepositorio suscripciones;
    private final AuditoriaServicio auditoria;

    @Transactional(readOnly = true)
    public Page<SuscripcionPlataformaResponse> listar(String estado, int pagina) {
        String filtro = estado == null || estado.isBlank() ? null : estadoValido(estado);
        return suscripciones.buscarParaPlataforma(filtro, PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA))
                .map(fila -> new SuscripcionPlataformaResponse(fila.getTiendaId(), fila.getSubdominio(),
                        fila.getCodigoPlan(), fila.getEstado(), fila.getFinPeriodoActual()));
    }

    /** Vitrina cerrada hasta reactivar; el estado previo queda guardado (SUS-010). */
    @Transactional
    public void suspender(UUID tiendaId, String motivo) {
        SuscripcionTiendaEntidad suscripcion = bloqueada(tiendaId);
        EstadoSuscripcion antes = suscripcion.getEstado();
        if (!antes.puedeTransicionarA(EstadoSuscripcion.SUSPENDIDA)) {
            throw new ConflictoEstadoException("SUS-010",
                    "No se puede suspender una tienda con su suscripción en estado " + antes.name());
        }
        suscripcion.setEstadoAnterior(antes);
        suscripcion.setEstado(EstadoSuscripcion.SUSPENDIDA);
        suscripcion.setMotivoEstado(motivo.strip());
        suscripciones.save(suscripcion);
        auditoria.registrar(tiendaId, AccionAuditoria.TIENDA_SUSPENDIDA, ENTIDAD, suscripcion.getId(), motivo.strip(),
                Map.of("antes", Map.of("estado", antes.name()), "despues", Map.of("estado", EstadoSuscripcion.SUSPENDIDA.name())));
    }

    /** Vuelve al estado anterior guardado al suspender (SUS-010). */
    @Transactional
    public void reactivar(UUID tiendaId, String motivo) {
        SuscripcionTiendaEntidad suscripcion = bloqueada(tiendaId);
        EstadoSuscripcion destino = suscripcion.getEstadoAnterior();
        if (suscripcion.getEstado() != EstadoSuscripcion.SUSPENDIDA || destino == null
                || !EstadoSuscripcion.SUSPENDIDA.puedeTransicionarA(destino)) {
            throw new ConflictoEstadoException("SUS-010", "La tienda no está suspendida");
        }
        suscripcion.setEstado(destino);
        suscripcion.setEstadoAnterior(null);
        suscripcion.setMotivoEstado(null);
        suscripciones.save(suscripcion);
        auditoria.registrar(tiendaId, AccionAuditoria.TIENDA_REACTIVADA, ENTIDAD, suscripcion.getId(), motivo.strip(),
                Map.of("antes", Map.of("estado", EstadoSuscripcion.SUSPENDIDA.name()), "despues", Map.of("estado", destino.name())));
    }

    private SuscripcionTiendaEntidad bloqueada(UUID tiendaId) {
        return suscripciones.bloquearNoCanceladaPorTienda(tiendaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "La tienda no tiene una suscripción vigente"));
    }

    private static String estadoValido(String estado) {
        try {
            return EstadoSuscripcion.valueOf(estado.strip().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new SolicitudInvalidaException("VAL-003", "El parámetro «estado» no tiene un valor válido");
        }
    }
}
