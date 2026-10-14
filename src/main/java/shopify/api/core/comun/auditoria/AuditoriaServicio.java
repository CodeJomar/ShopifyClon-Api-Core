package shopify.api.core.comun.auditoria;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.SesionActual;

/** Registra acciones sensibles en la misma transacción del cambio (AUD-001). */
@Service
@RequiredArgsConstructor
public class AuditoriaServicio {

    private final RegistroAuditoriaRepositorio repositorio;

    /** Falla si no hay una transacción activa: la auditoría nunca se escribe suelta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(UUID tiendaId, AccionAuditoria accion, String entidad, UUID entidadId,
                          String motivo, Map<String, Object> detalle) {
        RegistroAuditoriaEntidad registro = new RegistroAuditoriaEntidad();
        registro.setTiendaId(tiendaId);
        registro.setUsuarioId(SesionActual.usuarioIdOpcional().orElse(null));
        registro.setAccion(accion.name());
        registro.setEntidad(entidad);
        registro.setEntidadId(entidadId);
        registro.setMotivo(motivo);
        registro.setDetalle(detalle);
        registro.setIdCorrelacion(RespuestaApi.correlacionActual());
        repositorio.save(registro);
    }
}
