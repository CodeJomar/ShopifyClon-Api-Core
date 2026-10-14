package shopify.api.core.modulos.usuario.servicio;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.evento.PublicadorEvento;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.NoAutenticadoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.jwt.JwtProveedor;
import shopify.api.core.modulos.usuario.dto.request.ActivarCuentaRequest;
import shopify.api.core.modulos.usuario.dto.request.LoginRequest;
import shopify.api.core.modulos.usuario.dto.request.RegistroRequest;
import shopify.api.core.modulos.usuario.dto.response.LoginResponse;
import shopify.api.core.modulos.usuario.dto.response.RegistroResponse;
import shopify.api.core.modulos.usuario.entidad.UsuarioEntidad;
import shopify.api.core.modulos.usuario.evento.CodigoActivacionEmitidoEvento;
import shopify.api.core.modulos.usuario.mapeador.UsuarioMapeador;
import shopify.api.core.modulos.usuario.modelo.TipoCodigoSeguridad;
import shopify.api.core.modulos.usuario.repositorio.UsuarioRepositorio;

@Service
@RequiredArgsConstructor
public class AutenticacionServicio {

    private static final String MENSAJE_LOGIN_INVALIDO = "Correo o contraseña incorrectos";

    private final UsuarioRepositorio usuarioRepositorio;
    private final CodigoSeguridadServicio codigoSeguridadServicio;
    private final PasswordEncoder passwordEncoder;
    private final PublicadorEvento publicadorEvento;
    private final JwtProveedor jwtProveedor;
    private final UsuarioMapeador usuarioMapeador;
    private final Clock reloj;

    /** Hash que se compara cuando el correo no existe, para que el tiempo de respuesta sea parecido (USR-004). */
    private String hashFicticio;

    @PostConstruct
    void prepararHashFicticio() {
        hashFicticio = passwordEncoder.encode("clave-ficticia-" + UUID.randomUUID());
    }

    /** USR-001, USR-003: crea la cuenta sin activar y envía el código de activación. */
    @Transactional
    public RegistroResponse registrar(RegistroRequest request) {
        String correo = normalizar(request.correoElectronico());
        if (usuarioRepositorio.existePorCorreo(correo)) {
            throw correoEnUso();
        }

        UsuarioEntidad usuario = new UsuarioEntidad();
        usuario.setCorreoElectronico(correo);
        usuario.setContrasenaHash(passwordEncoder.encode(request.contrasena()));
        usuario.setNombres(request.nombres().trim());
        usuario.setApellidos(request.apellidos().trim());
        usuario.setTelefono(textoOpcional(request.telefono()));
        usuario.setEstaActivo(false);

        try {
            usuario = usuarioRepositorio.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            // Carrera con otro registro del mismo correo: UxUsuariosCorreoMinusculas
            throw correoEnUso();
        }

        String codigo = codigoSeguridadServicio.emitir(usuario.getId(), TipoCodigoSeguridad.ACTIVACION_CUENTA);
        publicadorEvento.publicar(new CodigoActivacionEmitidoEvento(correo, usuario.getNombres(), codigo));
        return new RegistroResponse(usuario.getId());
    }

    /** USR-003, USR-012: activa la cuenta con el código; una cuenta ya activa responde igual sin efectos. */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public void activar(ActivarCuentaRequest request) {
        UsuarioEntidad usuario = usuarioRepositorio.buscarVigentePorCorreo(normalizar(request.correoElectronico()))
                .orElseThrow(() -> new ReglaNegocioException("USR-012", CodigoSeguridadServicio.MENSAJE_CODIGO_INVALIDO));
        if (Boolean.TRUE.equals(usuario.getEstaActivo())) {
            return;
        }
        codigoSeguridadServicio.verificarYConsumir(usuario.getId(), TipoCodigoSeguridad.ACTIVACION_CUENTA, request.codigo());
        usuario.setEstaActivo(true);
        usuarioRepositorio.save(usuario);
    }

    /** USR-013, USR-006: solo envía si la cuenta existe, no está eliminada ni activa. */
    @Transactional
    public void reenviarActivacion(String correoElectronico) {
        usuarioRepositorio.buscarVigentePorCorreo(normalizar(correoElectronico))
                .filter(usuario -> !Boolean.TRUE.equals(usuario.getEstaActivo()))
                .ifPresent(usuario -> {
                    String codigo = codigoSeguridadServicio.emitir(usuario.getId(), TipoCodigoSeguridad.ACTIVACION_CUENTA);
                    publicadorEvento.publicar(
                            new CodigoActivacionEmitidoEvento(usuario.getCorreoElectronico(), usuario.getNombres(), codigo));
                });
    }

    /** USR-004: mismo error para correo inexistente o clave incorrecta; cuenta sin activar solo se revela con clave correcta. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<UsuarioEntidad> encontrado =
                usuarioRepositorio.buscarVigentePorCorreo(normalizar(request.correoElectronico()));

        if (encontrado.isEmpty()) {
            passwordEncoder.matches(request.contrasena(), hashFicticio);
            throw loginInvalido();
        }

        UsuarioEntidad usuario = encontrado.get();
        if (!passwordEncoder.matches(request.contrasena(), usuario.getContrasenaHash())) {
            throw loginInvalido();
        }
        if (!Boolean.TRUE.equals(usuario.getEstaActivo())) {
            throw new ConflictoEstadoException("USR-003", "Tu cuenta aún no está activada. Revisa tu correo");
        }

        Instant emitido = reloj.instant();
        String token = jwtProveedor.generarToken(usuario.getId(), usuario.getCorreoElectronico(), Map.of());
        return new LoginResponse(
                token,
                "Bearer",
                jwtProveedor.calcularExpiracion(emitido),
                usuarioMapeador.aPerfil(usuario),
                usuarioMapeador.aTiendas(usuarioRepositorio.listarTiendasDeUsuario(usuario.getId())));
    }

    static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static NoAutenticadoException loginInvalido() {
        return new NoAutenticadoException("USR-004", MENSAJE_LOGIN_INVALIDO);
    }

    private static ReglaNegocioException correoEnUso() {
        return new ReglaNegocioException("USR-001", "Ya existe una cuenta con ese correo");
    }
}
