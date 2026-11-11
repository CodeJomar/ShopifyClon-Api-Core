package shopify.api.core.modulos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import shopify.api.core.comun.seguridad.UsuarioAutenticado;
import shopify.api.core.comun.seguridad.UsuarioSesionRepositorio;
import shopify.api.core.comun.util.GeneradorToken;
import shopify.api.core.modulos.usuario.dto.response.TicketWebSocketResponse;
import shopify.api.core.modulos.usuario.servicio.TicketWebSocketServicio;

/** Pruebas unitarias de emisión, un solo uso y expiración temporal de tickets WebSocket. */
class TicketWebSocketServicioTest {

    private final UUID usuarioId = UUID.randomUUID();
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-11T12:00:00Z"));
    private UsuarioSesionRepositorio usuarioSesionRepositorio;
    private TicketWebSocketServicio servicio;

    @BeforeEach
    void preparar() {
        usuarioSesionRepositorio = mock(UsuarioSesionRepositorio.class);
        when(usuarioSesionRepositorio.buscarActivoPorId(eq(usuarioId), any(Instant.class)))
                .thenReturn(Optional.of(new UsuarioAutenticado(usuarioId, "tienda@prueba.test", false)));

        Clock reloj = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return ahora.get();
            }
        };
        servicio = new TicketWebSocketServicio(new GeneradorToken(), usuarioSesionRepositorio, reloj, 60);

        UsuarioAutenticado principal = new UsuarioAutenticado(usuarioId, "tienda@prueba.test", false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elTicketEsDeUnSoloUsoYRespetaTtl() {
        TicketWebSocketResponse emitido = servicio.emitirParaUsuarioActual();
        assertThat(emitido.ticket()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(emitido.ttlSegundos()).isEqualTo(60);

        ahora.set(Instant.parse("2026-11-11T12:00:59Z"));
        assertThat(servicio.validarYConsumir(emitido.ticket())).isPresent();
        assertThat(servicio.validarYConsumir(emitido.ticket())).isEmpty();
    }

    @Test
    void unTicketExpiradoORevocadoSeRechaza() {
        TicketWebSocketResponse expirado = servicio.emitirParaUsuarioActual();
        ahora.set(Instant.parse("2026-11-11T12:01:00Z"));
        assertThat(servicio.validarYConsumir(expirado.ticket())).isEmpty();

        TicketWebSocketResponse revocado = servicio.emitirParaUsuarioActual();
        when(usuarioSesionRepositorio.buscarActivoPorId(eq(usuarioId), any(Instant.class)))
                .thenReturn(Optional.empty());
        assertThat(servicio.validarYConsumir(revocado.ticket())).isEmpty();
    }
}
