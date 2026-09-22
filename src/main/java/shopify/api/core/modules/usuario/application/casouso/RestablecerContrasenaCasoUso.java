package shopify.api.core.modules.usuario.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.usuario.application.dto.request.RestablecerContrasenaRequest;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.TokenSeguridadEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.UsuarioEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.TokenSeguridadJpaRepositorio;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.UsuarioJpaRepositorio;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.ReglaNegocioException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RestablecerContrasenaCasoUso {

    private final TokenSeguridadJpaRepositorio tokenRepositorio;
    private final UsuarioJpaRepositorio usuarioJpaRepositorio;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public EstadoOperacionDto ejecutar(RestablecerContrasenaRequest request) {
        TokenSeguridadEntidad tokenEntidad = tokenRepositorio
                .findByCodigoTokenAndTipoAndUsadoFalseAndFechaExpiracionAfter(
                        request.getCodigo().trim(), "RECUPERACION_CLAVE", Instant.now())
                .orElseThrow(() -> new ReglaNegocioException("El código de verificación es inválido o ha expirado"));

        UsuarioEntidad usuario = tokenEntidad.getUsuario();

        if (!usuario.getCorreoElectronico().equalsIgnoreCase(request.getCorreoElectronico().trim())) {
            throw new ReglaNegocioException("El código no corresponde al correo indicado");
        }

        // Hashear y actualizar contraseña
        usuario.setContrasenaHash(passwordEncoder.encode(request.getNuevaContrasena()));
        usuarioJpaRepositorio.save(usuario);

        // Quemar el token para que no se use dos veces
        tokenEntidad.setUsado(true);
        tokenRepositorio.save(tokenEntidad);

        return EstadoOperacionDto.exitoso(usuario.getId(), "Contraseña restablecida exitosamente. Ya puede iniciar sesión.");
    }
}