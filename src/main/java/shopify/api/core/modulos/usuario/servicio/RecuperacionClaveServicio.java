package shopify.api.core.modulos.usuario.servicio;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.evento.PublicadorEvento;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.usuario.dto.request.RestablecerClaveRequest;
import shopify.api.core.modulos.usuario.entidad.UsuarioEntidad;
import shopify.api.core.modulos.usuario.evento.CodigoRecuperacionEmitidoEvento;
import shopify.api.core.modulos.usuario.modelo.TipoCodigoSeguridad;
import shopify.api.core.modulos.usuario.repositorio.UsuarioRepositorio;

@Service
@RequiredArgsConstructor
public class RecuperacionClaveServicio {

    private final UsuarioRepositorio usuarioRepositorio;
    private final CodigoSeguridadServicio codigoSeguridadServicio;
    private final ContrasenasComunes contrasenasComunes;
    private final PasswordEncoder passwordEncoder;
    private final PublicadorEvento publicadorEvento;

    /** USR-005, USR-006: solo envía el código si la cuenta existe y no está eliminada. */
    @Transactional
    public void solicitar(String correoElectronico) {
        usuarioRepositorio.buscarVigentePorCorreo(AutenticacionServicio.normalizar(correoElectronico))
                .ifPresent(usuario -> {
                    String codigo = codigoSeguridadServicio.emitir(usuario.getId(), TipoCodigoSeguridad.RECUPERACION_CLAVE);
                    publicadorEvento.publicar(
                            new CodigoRecuperacionEmitidoEvento(usuario.getCorreoElectronico(), usuario.getNombres(), codigo));
                });
    }

    /** USR-012, USR-006, USR-002: cambia la clave con el código; no activa cuentas sin activar. */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public void confirmar(RestablecerClaveRequest request) {
        UsuarioEntidad usuario = usuarioRepositorio
                .buscarVigentePorCorreo(AutenticacionServicio.normalizar(request.correoElectronico()))
                .orElseThrow(() -> new ReglaNegocioException("USR-012", CodigoSeguridadServicio.MENSAJE_CODIGO_INVALIDO));

        // Antes de consumir el código: una clave rechazada no debe gastar el código
        contrasenasComunes.exigirSegura(request.contrasenaNueva(), usuario.getCorreoElectronico());
        codigoSeguridadServicio.verificarYConsumir(usuario.getId(), TipoCodigoSeguridad.RECUPERACION_CLAVE, request.codigo());

        usuario.setContrasenaHash(passwordEncoder.encode(request.contrasenaNueva()));
        // A-007: restablecer la clave cierra las sesiones abiertas y levanta el bloqueo por intentos fallidos
        usuario.setSesionesValidasDesde(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        usuario.setIntentosLoginFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepositorio.save(usuario);
        codigoSeguridadServicio.invalidarPendientes(usuario.getId(), TipoCodigoSeguridad.RECUPERACION_CLAVE);
    }
}
