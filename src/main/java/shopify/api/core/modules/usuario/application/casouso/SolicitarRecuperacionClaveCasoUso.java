package shopify.api.core.modules.usuario.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.usuario.application.dto.request.SolicitarRecuperacionRequest;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.TokenSeguridadEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.UsuarioEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.TokenSeguridadJpaRepositorio;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.UsuarioJpaRepositorio;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.event.CodigoRecuperacionGeneradoEvento;
import shopify.api.core.shared.event.PublicadorEvento;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SolicitarRecuperacionClaveCasoUso {

    private final UsuarioJpaRepositorio usuarioJpaRepositorio;
    private final TokenSeguridadJpaRepositorio tokenRepositorio;
    private final PublicadorEvento publicadorEvento;

    @Transactional
    public EstadoOperacionDto ejecutar(SolicitarRecuperacionRequest request) {
        String correo = request.getCorreoElectronico().trim().toLowerCase();
        Optional<UsuarioEntidad> usuarioOpt = usuarioJpaRepositorio.findByCorreoElectronicoAndEliminadoFalse(correo);

        // Por seguridad (OWASP), siempre respondemos lo mismo aunque el correo no exista
        if (usuarioOpt.isEmpty()) {
            return EstadoOperacionDto.builder()
                    .exito(true)
                    .codigoEstado(200)
                    .mensaje("Si el correo existe en nuestro sistema, recibirá un código de recuperación")
                    .build();
        }

        UsuarioEntidad usuario = usuarioOpt.get();

        // Generar código numérico de 6 dígitos (ej: 489215)
        String codigoOtp = String.format("%06d", new SecureRandom().nextInt(999999));

        TokenSeguridadEntidad tokenEntidad = new TokenSeguridadEntidad();
        tokenEntidad.setCodigoToken(codigoOtp);
        tokenEntidad.setTipo("RECUPERACION_CLAVE");
        tokenEntidad.setFechaExpiracion(Instant.now().plus(15, ChronoUnit.MINUTES));
        tokenEntidad.setUsado(false);
        tokenEntidad.setUsuario(usuario);
        tokenRepositorio.save(tokenEntidad);

        // Enviar evento para notificar por email
        publicadorEvento.publicar(CodigoRecuperacionGeneradoEvento.builder()
                .idUsuario(usuario.getId())
                .correoElectronico(usuario.getCorreoElectronico())
                .nombres(usuario.getNombres())
                .codigoOtp(codigoOtp)
                .build());

        return EstadoOperacionDto.builder()
                .exito(true)
                .codigoEstado(200)
                .mensaje("Si el correo existe en nuestro sistema, recibirá un código de recuperación")
                .build();
    }
}