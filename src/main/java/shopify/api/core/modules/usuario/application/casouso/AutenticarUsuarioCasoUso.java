package shopify.api.core.modules.usuario.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.usuario.application.dto.request.LoginUsuarioRequest;
import shopify.api.core.modules.usuario.application.dto.response.AuthResponse;
import shopify.api.core.modules.usuario.domain.modelo.Usuario;
import shopify.api.core.modules.usuario.domain.puerto.salida.UsuarioRepositorioPuerto;
import shopify.api.core.shared.exception.ReglaNegocioException;
import shopify.api.core.shared.seguridad.jwt.JwtProveedor;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutenticarUsuarioCasoUso {

    private final UsuarioRepositorioPuerto usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final JwtProveedor jwtProveedor;

    @Transactional(readOnly = true)
    public AuthResponse ejecutar(LoginUsuarioRequest request) {
        String correo = request.getCorreoElectronico().trim().toLowerCase();

        Usuario usuario = usuarioRepositorio.buscarPorCorreo(correo)
                .orElseThrow(() -> new ReglaNegocioException("Credenciales inválidas"));

        if (!Boolean.TRUE.equals(usuario.getEstaActivo())) {
            throw new ReglaNegocioException("La cuenta de usuario se encuentra suspendida o inactiva");
        }

        if (!passwordEncoder.matches(request.getContrasena(), usuario.getContrasenaHash())) {
            throw new ReglaNegocioException("Credenciales inválidas");
        }

        String codigoRol = usuario.getRol() != null ? usuario.getRol().getCodigo() : "CLIENTE";

        Map<String, Object> claims = new HashMap<>();
        claims.put("rol", codigoRol);
        claims.put("nombres", usuario.getNombres());
        claims.put("apellidos", usuario.getApellidos());

        String token = jwtProveedor.generarToken(usuario.getId(), usuario.getCorreoElectronico(), claims);

        return AuthResponse.builder()
                .tokenAcceso(token)
                .tipoToken("Bearer")
                .expiraEnMs(86400000L) // 24 horas
                .idUsuario(usuario.getId())
                .correoElectronico(usuario.getCorreoElectronico())
                .nombreCompleto(usuario.getNombres() + " " + usuario.getApellidos())
                .rol(codigoRol)
                .build();
    }
}