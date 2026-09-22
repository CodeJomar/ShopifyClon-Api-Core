package shopify.api.core.modules.usuario.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.TokenSeguridadEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.UsuarioEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.TokenSeguridadJpaRepositorio;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.UsuarioJpaRepositorio;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.ReglaNegocioException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ActivarCuentaUsuarioCasoUso {

    private final TokenSeguridadJpaRepositorio tokenRepositorio;
    private final UsuarioJpaRepositorio usuarioJpaRepositorio;

    @Transactional
    public EstadoOperacionDto ejecutar(String token) {
        TokenSeguridadEntidad tokenEntidad = tokenRepositorio
                .findByCodigoTokenAndTipoAndUsadoFalseAndFechaExpiracionAfter(token, "ACTIVACION_CUENTA", Instant.now())
                .orElseThrow(() -> new ReglaNegocioException("El enlace de activación es inválido o ha expirado"));

        UsuarioEntidad usuario = tokenEntidad.getUsuario();
        usuario.setEstaActivo(true);
        usuarioJpaRepositorio.save(usuario);

        tokenEntidad.setUsado(true);
        tokenRepositorio.save(tokenEntidad);

        return EstadoOperacionDto.exitoso(usuario.getId(), "Cuenta activada exitosamente. Ya puede iniciar sesión.");
    }
}