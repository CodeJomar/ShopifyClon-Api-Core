package shopify.api.core.modulos.usuario.servicio;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.NoAutenticadoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.usuario.dto.request.ActualizarPerfilRequest;
import shopify.api.core.modulos.usuario.dto.request.CambiarContrasenaRequest;
import shopify.api.core.modulos.usuario.dto.response.PerfilResponse;
import shopify.api.core.modulos.usuario.entidad.UsuarioEntidad;
import shopify.api.core.modulos.usuario.mapeador.UsuarioMapeador;
import shopify.api.core.modulos.usuario.repositorio.UsuarioRepositorio;

@Service
@RequiredArgsConstructor
public class CuentaServicio {

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapeador usuarioMapeador;

    @Transactional(readOnly = true)
    public PerfilResponse perfil() {
        return usuarioMapeador.aPerfil(usuarioActual());
    }

    @Transactional
    public PerfilResponse actualizarPerfil(ActualizarPerfilRequest request) {
        UsuarioEntidad usuario = usuarioActual();
        usuario.setNombres(request.nombres().trim());
        usuario.setApellidos(request.apellidos().trim());
        usuario.setTelefono(request.telefono() == null || request.telefono().isBlank() ? null : request.telefono().trim());
        return usuarioMapeador.aPerfil(usuarioRepositorio.save(usuario));
    }

    /** USR-010: exige la contraseña actual y que la nueva sea distinta. */
    @Transactional
    public void cambiarContrasena(CambiarContrasenaRequest request) {
        UsuarioEntidad usuario = usuarioActual();
        if (!passwordEncoder.matches(request.contrasenaActual(), usuario.getContrasenaHash())) {
            throw new ReglaNegocioException("USR-010", "La contraseña actual no es correcta");
        }
        if (passwordEncoder.matches(request.contrasenaNueva(), usuario.getContrasenaHash())) {
            throw new ReglaNegocioException("USR-010", "La nueva contraseña no puede ser igual a la actual");
        }
        usuario.setContrasenaHash(passwordEncoder.encode(request.contrasenaNueva()));
        usuarioRepositorio.save(usuario);
    }

    private UsuarioEntidad usuarioActual() {
        UUID usuarioId = SesionActual.usuarioId();
        return usuarioRepositorio.findById(usuarioId)
                .filter(usuario -> !Boolean.TRUE.equals(usuario.getEliminado()))
                .orElseThrow(() -> new NoAutenticadoException("SEG-007", "Inicia sesión para continuar"));
    }
}
