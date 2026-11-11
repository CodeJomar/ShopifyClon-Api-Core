package shopify.api.core.modulos.usuario.servicio;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import shopify.api.core.comun.excepcion.ExcepcionNegocio;
import shopify.api.core.comun.excepcion.NoAutenticadoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.jwt.JwtProveedor;
import shopify.api.core.modulos.usuario.dto.request.ActivarCuentaRequest;
import shopify.api.core.modulos.usuario.dto.request.CodigoCorreoRequest;
import shopify.api.core.modulos.usuario.dto.request.LoginRequest;
import shopify.api.core.modulos.usuario.dto.request.RegistroRequest;
import shopify.api.core.modulos.usuario.dto.response.LoginResponse;
import shopify.api.core.modulos.usuario.dto.response.RegistroResponse;
import shopify.api.core.modulos.usuario.entidad.UsuarioEntidad;
import shopify.api.core.modulos.usuario.evento.CodigoActivacionEmitidoEvento;
import shopify.api.core.modulos.usuario.evento.CodigoSegundoFactorEmitidoEvento;
import shopify.api.core.modulos.usuario.evento.CuentaBloqueadaEvento;
import shopify.api.core.modulos.usuario.mapeador.UsuarioMapeador;
import shopify.api.core.modulos.usuario.modelo.TipoCodigoSeguridad;
import shopify.api.core.modulos.usuario.repositorio.UsuarioRepositorio;

@Service
@RequiredArgsConstructor
public class AutenticacionServicio {

    private static final String MENSAJE_LOGIN_INVALIDO = "Correo o contraseña incorrectos";

    /** A-007: fallos seguidos de contraseña que bloquean la cuenta, y cuánto dura el bloqueo. */
    static final int MAX_INTENTOS_LOGIN = 5;
    static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private final UsuarioRepositorio usuarioRepositorio;
    private final CodigoSeguridadServicio codigoSeguridadServicio;
    private final ContrasenasComunes contrasenasComunes;
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
        contrasenasComunes.exigirSegura(request.contrasena(), correo);
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

    /**
     * USR-004: mismo error para correo inexistente o clave incorrecta; cuenta sin activar solo se revela con clave correcta.
     * A-007: tras {@link #MAX_INTENTOS_LOGIN} fallos seguidos la cuenta se bloquea {@link #DURACION_BLOQUEO} (USR-014) y
     * el dueño recibe un correo con un código para desbloquearla. Con la verificación en dos pasos activa, la contraseña
     * correcta no entrega token: envía un código y el login termina en {@link #verificarSegundoFactor}.
     * {@code noRollbackFor}: el contador de fallos y el bloqueo deben guardarse aunque se responda con error.
     */
    @Transactional(noRollbackFor = ExcepcionNegocio.class)
    public LoginResponse login(LoginRequest request) {
        Optional<UsuarioEntidad> encontrado =
                usuarioRepositorio.buscarVigentePorCorreo(normalizar(request.correoElectronico()));

        if (encontrado.isEmpty()) {
            passwordEncoder.matches(request.contrasena(), hashFicticio);
            throw loginInvalido();
        }

        UsuarioEntidad usuario = encontrado.get();
        Instant ahora = reloj.instant();
        if (estaBloqueado(usuario, ahora)) {
            // Mismo costo que una comparación real, para no distinguir por tiempo
            passwordEncoder.matches(request.contrasena(), hashFicticio);
            throw cuentaBloqueada();
        }
        if (!passwordEncoder.matches(request.contrasena(), usuario.getContrasenaHash())) {
            if (registrarFallo(usuario, ahora)) {
                throw cuentaBloqueada();
            }
            throw loginInvalido();
        }
        if (!Boolean.TRUE.equals(usuario.getEstaActivo())) {
            throw new ConflictoEstadoException("USR-003", "Tu cuenta aún no está activada. Revisa tu correo");
        }
        limpiarBloqueo(usuario);

        if (Boolean.TRUE.equals(usuario.getDosPasosActivo())) {
            String codigo = codigoSeguridadServicio.emitir(usuario.getId(), TipoCodigoSeguridad.SEGUNDO_FACTOR);
            publicadorEvento.publicar(
                    new CodigoSegundoFactorEmitidoEvento(usuario.getCorreoElectronico(), usuario.getNombres(), codigo));
            return LoginResponse.pendienteDeSegundoFactor();
        }
        return sesionPara(usuario);
    }

    /** A-007: segundo paso del login. El código solo se emite tras una contraseña correcta; 5 intentos por código. */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public LoginResponse verificarSegundoFactor(CodigoCorreoRequest request) {
        UsuarioEntidad usuario = usuarioRepositorio.buscarVigentePorCorreo(normalizar(request.correoElectronico()))
                .filter(u -> Boolean.TRUE.equals(u.getEstaActivo()))
                .filter(u -> !estaBloqueado(u, reloj.instant()))
                .orElseThrow(AutenticacionServicio::codigoInvalido);
        codigoSeguridadServicio.verificarYConsumir(usuario.getId(), TipoCodigoSeguridad.SEGUNDO_FACTOR, request.codigo());
        return sesionPara(usuario);
    }

    /** A-007: el dueño desbloquea su cuenta con el código que recibió por correo, sin esperar a que venza el bloqueo. */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public void desbloquear(CodigoCorreoRequest request) {
        UsuarioEntidad usuario = usuarioRepositorio.buscarVigentePorCorreo(normalizar(request.correoElectronico()))
                .orElseThrow(AutenticacionServicio::codigoInvalido);
        codigoSeguridadServicio.verificarYConsumir(usuario.getId(), TipoCodigoSeguridad.DESBLOQUEO_CUENTA, request.codigo());
        usuario.setIntentosLoginFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepositorio.save(usuario);
    }

    private LoginResponse sesionPara(UsuarioEntidad usuario) {
        Instant emitido = Instant.now();
        String token = jwtProveedor.generarToken(usuario.getId(), usuario.getCorreoElectronico(), Map.of());
        return LoginResponse.sesion(
                token,
                jwtProveedor.calcularExpiracion(emitido),
                usuarioMapeador.aPerfil(usuario),
                usuarioMapeador.aTiendas(usuarioRepositorio.listarTiendasDeUsuario(usuario.getId())));
    }

    private static boolean estaBloqueado(UsuarioEntidad usuario, Instant ahora) {
        return usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora);
    }

    /** Cuenta el fallo; al llegar al tope bloquea la cuenta, avisa al dueño por correo y devuelve {@code true}. */
    private boolean registrarFallo(UsuarioEntidad usuario, Instant ahora) {
        int fallos = (usuario.getIntentosLoginFallidos() == null ? 0 : usuario.getIntentosLoginFallidos()) + 1;
        boolean bloquea = fallos >= MAX_INTENTOS_LOGIN;
        if (bloquea) {
            usuario.setIntentosLoginFallidos(0);
            usuario.setBloqueadoHasta(ahora.plus(DURACION_BLOQUEO));
            String codigo = codigoSeguridadServicio.emitir(usuario.getId(), TipoCodigoSeguridad.DESBLOQUEO_CUENTA);
            publicadorEvento.publicar(new CuentaBloqueadaEvento(
                    usuario.getCorreoElectronico(), usuario.getNombres(), codigo, DURACION_BLOQUEO.toMinutes()));
        } else {
            usuario.setIntentosLoginFallidos(fallos);
        }
        usuarioRepositorio.saveAndFlush(usuario);
        return bloquea;
    }

    private void limpiarBloqueo(UsuarioEntidad usuario) {
        boolean habiaFallos = usuario.getIntentosLoginFallidos() != null && usuario.getIntentosLoginFallidos() > 0;
        if (habiaFallos || usuario.getBloqueadoHasta() != null) {
            usuario.setIntentosLoginFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuarioRepositorio.save(usuario);
        }
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

    private static ConflictoEstadoException cuentaBloqueada() {
        return new ConflictoEstadoException("USR-014",
                "Tu cuenta está bloqueada temporalmente por varios intentos fallidos. Revisa tu correo para "
                        + "desbloquearla o inténtalo más tarde");
    }

    private static ReglaNegocioException codigoInvalido() {
        return new ReglaNegocioException("USR-012", CodigoSeguridadServicio.MENSAJE_CODIGO_INVALIDO);
    }

    private static ReglaNegocioException correoEnUso() {
        return new ReglaNegocioException("USR-001", "Ya existe una cuenta con ese correo");
    }
}
