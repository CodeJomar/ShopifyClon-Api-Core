package shopify.api.core.comun.websocket;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import shopify.api.core.comun.seguridad.MembresiaTiendaRepositorio;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RolTienda;
import shopify.api.core.comun.seguridad.UsuarioAutenticado;
import shopify.api.core.comun.seguridad.UsuarioSesionRepositorio;
import shopify.api.core.comun.seguridad.jwt.JwtProveedor;
import shopify.api.core.comun.websocket.ValidadorTicketWs.SesionTicketWs;

/**
 * Autentica {@code CONNECT} (JWT o ticket efímero, o anónimo solo para órdenes públicas) y autoriza
 * cada {@code SUBSCRIBE} según rol/permiso en tienda o {@code token_acceso} de orden. Bloquea {@code SEND}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutenticacionStompInterceptor implements ChannelInterceptor {

    public static final String ENCABEZADO_AUTH = "Authorization";
    public static final String ENCABEZADO_TICKET = "X-Ws-Ticket";
    private static final String PREFIJO_BEARER = "Bearer ";

    private static final Pattern CANAL_TIENDA = Pattern.compile(
            "^/topic/tienda/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/ordenes$");
    private static final Pattern CANAL_ORDEN = Pattern.compile("^/topic/ordenes/([A-Za-z0-9_-]{8,100})$");

    private final JwtProveedor jwtProveedor;
    private final UsuarioSesionRepositorio usuarioSesionRepositorio;
    private final MembresiaTiendaRepositorio membresiaTiendaRepositorio;
    private final ValidadorTicketWs validadorTicketWs;
    private final VerificadorAccesoOrdenWs verificadorAccesoOrdenWs;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> autenticarConexion(accessor);
            case SUBSCRIBE -> autorizarSuscripcion(accessor);
            case SEND -> throw new AccessDeniedException("El envío de mensajes por WebSocket no está habilitado");
            default -> {
                // HEARTBEAT, DISCONNECT, ACK/NACK, UNSUBSCRIBE siguen el flujo normal
            }
        }
        return message;
    }

    private void autenticarConexion(StompHeaderAccessor accessor) {
        String auth = primerEncabezado(accessor, ENCABEZADO_AUTH);
        String ticket = primerEncabezado(accessor, ENCABEZADO_TICKET);

        boolean hayAuth = StringUtils.hasText(auth);
        boolean hayTicket = StringUtils.hasText(ticket);

        if (hayAuth && hayTicket) {
            throw new AccessDeniedException("Envía solo Authorization o X-Ws-Ticket al conectar");
        }
        if (hayTicket) {
            SesionTicketWs sesion = validadorTicketWs.validarYConsumir(ticket.strip())
                    .orElseThrow(() -> new AccessDeniedException("Ticket WebSocket inválido o expirado"));
            accessor.setUser(new UsuarioStompPrincipal(sesion.usuario(), sesion.sesionEmitidaEn()));
            return;
        }
        if (hayAuth) {
            UsuarioStompPrincipal principal = autenticarJwt(auth.strip());
            accessor.setUser(principal);
        }
        // Sin credenciales: conexión anónima; solo podrá suscribirse a /topic/ordenes/{tokenAcceso} válido
    }

    private UsuarioStompPrincipal autenticarJwt(String encabezadoAuth) {
        if (!encabezadoAuth.startsWith(PREFIJO_BEARER)) {
            throw new AccessDeniedException("Formato de autorización WebSocket inválido");
        }
        String token = encabezadoAuth.substring(PREFIJO_BEARER.length()).strip();
        if (!StringUtils.hasText(token) || !jwtProveedor.esTokenValido(token)) {
            throw new AccessDeniedException("Sesión WebSocket inválida o expirada");
        }
        UUID usuarioId = jwtProveedor.extraerUsuarioId(token);
        Instant emitido = jwtProveedor.extraerEmitido(token);
        UsuarioAutenticado usuario = usuarioSesionRepositorio.buscarActivoPorId(usuarioId, emitido)
                .orElseThrow(() -> new AccessDeniedException("Sesión WebSocket inválida o revocada"));
        return new UsuarioStompPrincipal(usuario, emitido);
    }

    private void autorizarSuscripcion(StompHeaderAccessor accessor) {
        String destino = accessor.getDestination();
        if (!StringUtils.hasText(destino)) {
            throw new AccessDeniedException("Destino WebSocket obligatorio");
        }

        Matcher tienda = CANAL_TIENDA.matcher(destino);
        if (tienda.matches()) {
            autorizarCanalTienda(accessor, parsearUuid(tienda.group(1)));
            return;
        }

        Matcher orden = CANAL_ORDEN.matcher(destino);
        if (orden.matches()) {
            if (!verificadorAccesoOrdenWs.existeOrdenActivaPorTokenAcceso(orden.group(1))) {
                throw new AccessDeniedException("No tienes acceso a esa orden");
            }
            return;
        }

        throw new AccessDeniedException("Canal WebSocket no permitido");
    }

    private void autorizarCanalTienda(StompHeaderAccessor accessor, UUID tiendaId) {
        if (!(accessor.getUser() instanceof UsuarioStompPrincipal principal)) {
            throw new AccessDeniedException("Inicia sesión para suscribirte a las órdenes de la tienda");
        }
        // Revalidar sesión activa por si el usuario cerró sesiones o fue desactivado tras abrir el socket
        boolean sesionVigente = usuarioSesionRepositorio
                .buscarActivoPorId(principal.usuario().id(), principal.credencialesEmitidasEn())
                .isPresent();
        if (!sesionVigente) {
            throw new AccessDeniedException("Tu sesión ya no está activa");
        }

        boolean permitido = membresiaTiendaRepositorio.buscarCodigoRol(tiendaId, principal.usuario().id())
                .flatMap(RolTienda::desdeCodigo)
                .map(rol -> rol.tiene(Permiso.ORDENES_VER))
                .orElse(false);
        if (!permitido) {
            throw new AccessDeniedException("No tienes permiso para ver las órdenes de esta tienda");
        }
    }

    private static UUID parsearUuid(String texto) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Identificador de tienda inválido");
        }
    }

    private static String primerEncabezado(StompHeaderAccessor accessor, String nombre) {
        String valor = accessor.getFirstNativeHeader(nombre);
        if (valor != null) {
            return valor;
        }
        return Optional.ofNullable(accessor.getFirstNativeHeader(nombre.toLowerCase())).orElse(null);
    }
}
