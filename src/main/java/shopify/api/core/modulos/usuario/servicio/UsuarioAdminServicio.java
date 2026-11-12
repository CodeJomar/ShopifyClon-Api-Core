package shopify.api.core.modulos.usuario.servicio;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.usuario.entidad.UsuarioEntidad;
import shopify.api.core.modulos.usuario.repositorio.UsuarioRepositorio;

/**
 * Acciones del administrador de plataforma sobre una cuenta (R-001, B8): bloquear, desbloquear y cerrar sesiones.
 * Cada una queda en la auditoría como evento de seguridad. Nadie se bloquea a sí mismo.
 */
@Service
@RequiredArgsConstructor
public class UsuarioAdminServicio {

    /** Un bloqueo del administrador no vence solo: hay que desbloquear. */
    static final Instant SIN_VENCIMIENTO = Instant.parse("9999-12-31T00:00:00Z");

    private final UsuarioRepositorio usuarios;
    private final AuditoriaServicio auditoria;

    @Transactional
    public void bloquear(UUID usuarioId, String motivo) {
        UsuarioEntidad usuario = buscar(usuarioId);
        if (usuarioId.equals(SesionActual.usuarioId())) {
            throw new ReglaNegocioException("USR-015", "No puedes bloquear tu propia cuenta");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("VAL-001", "Indica el motivo del bloqueo");
        }
        usuario.setBloqueadoHasta(SIN_VENCIMIENTO);
        usuario.setSesionesValidasDesde(ahora());
        usuarios.save(usuario);
        auditoria.registrar(null, AccionAuditoria.SEGURIDAD_USUARIO_BLOQUEADO, "Usuarios", usuarioId, motivo.strip(),
                Map.of("correo", usuario.getCorreoElectronico()));
    }

    @Transactional
    public void desbloquear(UUID usuarioId) {
        UsuarioEntidad usuario = buscar(usuarioId);
        usuario.setBloqueadoHasta(null);
        usuario.setIntentosLoginFallidos(0);
        usuarios.save(usuario);
        auditoria.registrar(null, AccionAuditoria.SEGURIDAD_USUARIO_DESBLOQUEADO, "Usuarios", usuarioId, null,
                Map.of("correo", usuario.getCorreoElectronico()));
    }

    /** Los JWT ya emitidos de esa persona responden 401 desde ahora. */
    @Transactional
    public void cerrarSesiones(UUID usuarioId) {
        UsuarioEntidad usuario = buscar(usuarioId);
        usuario.setSesionesValidasDesde(ahora());
        usuarios.save(usuario);
        auditoria.registrar(null, AccionAuditoria.SEGURIDAD_SESIONES_CERRADAS, "Usuarios", usuarioId, "Cierre por administrador",
                Map.of("correo", usuario.getCorreoElectronico()));
    }

    private UsuarioEntidad buscar(UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .filter(u -> !Boolean.TRUE.equals(u.getEliminado()))
                .orElseThrow(() -> new RecursoNoEncontradoException("USR-404", "No encontramos ese usuario"));
    }

    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
