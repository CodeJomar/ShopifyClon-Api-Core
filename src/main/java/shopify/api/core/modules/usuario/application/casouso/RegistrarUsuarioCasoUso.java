package shopify.api.core.modules.usuario.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.usuario.application.dto.request.RegistroUsuarioRequest;
import shopify.api.core.modules.usuario.domain.modelo.Rol;
import shopify.api.core.modules.usuario.domain.modelo.Usuario;
import shopify.api.core.modules.usuario.domain.puerto.salida.UsuarioRepositorioPuerto;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.TokenSeguridadEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.UsuarioEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.TokenSeguridadJpaRepositorio;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.UsuarioJpaRepositorio;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.event.PublicadorEvento;
import shopify.api.core.shared.event.UsuarioRegistradoEvento;
import shopify.api.core.shared.exception.ReglaNegocioException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrarUsuarioCasoUso {

    private final UsuarioRepositorioPuerto usuarioRepositorio;
    private final UsuarioJpaRepositorio usuarioJpaRepositorio;
    private final TokenSeguridadJpaRepositorio tokenRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final PublicadorEvento publicadorEvento;

    @Transactional
    public EstadoOperacionDto ejecutar(RegistroUsuarioRequest request) {
        String correoLimpio = request.getCorreoElectronico().trim().toLowerCase();

        if (usuarioRepositorio.existePorCorreo(correoLimpio)) {
            throw new ReglaNegocioException("Ya existe una cuenta registrada con el correo: " + correoLimpio);
        }

        Rol rol = usuarioRepositorio.buscarRolPorCodigo(request.getCodigoRol())
                .orElseGet(() -> usuarioRepositorio.guardarRol(Rol.builder()
                        .codigo(request.getCodigoRol())
                        .nombre(request.getCodigoRol().equals("ADMIN_TIENDA") ? "Administrador de Tienda" : "Cliente Comprador")
                        .descripcion("Rol generado automáticamente por el sistema")
                        .build()));

        // Nace inactivo hasta que confirme por correo
        Usuario nuevoUsuario = Usuario.builder()
                .correoElectronico(correoLimpio)
                .contrasenaHash(passwordEncoder.encode(request.getContrasena()))
                .nombres(request.getNombres().trim())
                .apellidos(request.getApellidos().trim())
                .telefono(request.getTelefono())
                .estaActivo(false)
                .rol(rol)
                .build();

        Usuario guardado = usuarioRepositorio.guardar(nuevoUsuario);

        // Generar token de activación (válido por 48 horas)
        String tokenActivacion = UUID.randomUUID().toString();
        UsuarioEntidad usuarioEntidad = usuarioJpaRepositorio.getReferenceById(guardado.getId());

        TokenSeguridadEntidad tokenEntidad = new TokenSeguridadEntidad();
        tokenEntidad.setCodigoToken(tokenActivacion);
        tokenEntidad.setTipo("ACTIVACION_CUENTA");
        tokenEntidad.setFechaExpiracion(Instant.now().plus(48, ChronoUnit.HOURS));
        tokenEntidad.setUsado(false);
        tokenEntidad.setUsuario(usuarioEntidad);
        tokenRepositorio.save(tokenEntidad);

        // Despachar evento para enviar el correo en segundo plano
        publicadorEvento.publicar(UsuarioRegistradoEvento.builder()
                .idUsuario(guardado.getId())
                .correoElectronico(guardado.getCorreoElectronico())
                .nombres(guardado.getNombres())
                .tokenActivacion(tokenActivacion)
                .build());

        return EstadoOperacionDto.creado(
                guardado.getId(),
                guardado.getCorreoElectronico(),
                "Usuario registrado exitosamente. Hemos enviado un correo para activar su cuenta."
        );
    }
}