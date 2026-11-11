package shopify.api.core.modulos.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import shopify.api.core.modulos.envio.evento.EnvioDespachadoEvento;
import shopify.api.core.modulos.envio.evento.EnvioEntregadoEvento;
import shopify.api.core.modulos.orden.eventos.OrdenCreadaEvento;
import shopify.api.core.modulos.orden.eventos.OrdenPagadaEvento;
import shopify.api.core.modulos.pago.eventos.OrdenCanceladaEvento;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/**
 * Pruebas integrales de WebSocket STOMP: handshake CORS, autenticación JWT / ticket efímero,
 * autorización de canales por permiso ({@code ORDENES_VER}) o {@code token_acceso}, bloqueo de {@code SEND}
 * y entrega en tiempo real tras el commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketNotificacionesIntegrationTest extends PruebaIntegracionBase {

    @LocalServerPort
    private int puerto;

    @Autowired
    private ApplicationEventPublisher publicador;

    @Autowired
    private TransactionTemplate transaccion;

    private UUID propietarioId;
    private UUID tiendaId;
    private String jwtPropietario;

    @BeforeEach
    void prepararTienda() {
        propietarioId = crearUsuarioActivo(false);
        tiendaId = crearTienda(propietarioId);
        jwtPropietario = tokenPara(propietarioId, "prop@prueba.test");
    }

    // ---- endpoint HTTP /auth/ws-ticket ----

    @Test
    void postWsTicketExigeJwtValidoYRespetaRevocacion() throws Exception {
        postJson(Rutas.AUTH_WS_TICKET, null, null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SEG-007"));

        JsonNode respuesta = cuerpo(postJson(Rutas.AUTH_WS_TICKET, null, jwtPropietario, null)
                .andExpect(status().isOk()));
        assertThat(respuesta.path("datos").path("ticket").asText()).isNotBlank();
        assertThat(respuesta.path("datos").path("expira_en").asText()).isNotBlank();
        assertThat(respuesta.path("datos").path("ttl_segundos").asInt()).isEqualTo(60);

        postJson("/cuenta/cerrar-sesiones", null, jwtPropietario, null).andExpect(status().isOk());
        postJson(Rutas.AUTH_WS_TICKET, null, jwtPropietario, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SEG-007"));
    }

    @Test
    void laboratorioTiempoRealSirveHtmlYClienteStomp() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String base = "http://localhost:" + puerto + "/api/v1/laboratorio";

        HttpResponse<String> html = http.send(
                HttpRequest.newBuilder(URI.create(base + "/tiempo-real.html")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(html.statusCode()).isEqualTo(200);
        assertThat(html.body()).contains("stomp-cliente.js").contains("/topic/tienda/");

        HttpResponse<String> stompJs = http.send(
                HttpRequest.newBuilder(URI.create(base + "/assets/stomp-cliente.js")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(stompJs.statusCode()).isEqualTo(200);
        assertThat(stompJs.body()).contains("LabStomp");

        HttpResponse<String> indexHtml = http.send(
                HttpRequest.newBuilder(URI.create(base + "/index.html")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(indexHtml.statusCode()).isEqualTo(200);
        assertThat(indexHtml.body()).contains("tiempo-real.html");

        HttpResponse<String> labJs = http.send(
                HttpRequest.newBuilder(URI.create(base + "/assets/lab.js")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(labJs.statusCode()).isEqualTo(200);
        assertThat(labJs.body()).contains("tiempo-real.html");
    }

    // ---- handshake y autenticación STOMP ----

    @Test
    void handshakeRechazaOrigenNoPermitidoYAceptaOrigenConfigurado() throws Exception {
        WebSocketHttpHeaders origenMalo = new WebSocketHttpHeaders();
        origenMalo.setOrigin("https://atacante.test");
        assertThatThrownBy(() -> conectar(origenMalo, cabecerasJwt(jwtPropietario), new CompletableFuture<>()))
                .isInstanceOf(Exception.class);

        WebSocketHttpHeaders origenWeb = new WebSocketHttpHeaders();
        origenWeb.setOrigin("http://localhost:3000");
        StompSession sesion = conectar(origenWeb, cabecerasJwt(jwtPropietario), new CompletableFuture<>());
        assertThat(sesion.isConnected()).isTrue();
        sesion.disconnect();
    }

    @Test
    void connectRechazaJwtInvalidoInactivoORevocadoYTicketNoSeReutiliza() throws Exception {
        StompHeaders jwtMalo = new StompHeaders();
        jwtMalo.add("Authorization", "Bearer token.invalido.123");
        assertFallaConexion(jwtMalo);

        UUID inactivoId = crearUsuarioActivo(false);
        String jwtInactivo = tokenPara(inactivoId, "inactivo@prueba.test");
        inactivarUsuario(inactivoId);
        assertFallaConexion(cabecerasJwt(jwtInactivo));

        // Ticket de un solo uso
        String ticket = cuerpo(postJson(Rutas.AUTH_WS_TICKET, null, jwtPropietario, null).andExpect(status().isOk()))
                .path("datos").path("ticket").asText();
        StompHeaders conTicket = new StompHeaders();
        conTicket.add("X-Ws-Ticket", ticket);
        StompSession ok = conectar(new WebSocketHttpHeaders(), conTicket, new CompletableFuture<>());
        assertThat(ok.isConnected()).isTrue();
        ok.disconnect();

        // Segundo intento con el mismo ticket falla
        assertFallaConexion(conTicket);

        // Ticket emitido antes de cerrar-sesiones falla
        String ticketAntesDeCerrar = cuerpo(postJson(Rutas.AUTH_WS_TICKET, null, jwtPropietario, null)
                .andExpect(status().isOk())).path("datos").path("ticket").asText();
        Thread.sleep(5);
        postJson("/cuenta/cerrar-sesiones", null, jwtPropietario, null).andExpect(status().isOk());
        StompHeaders cabTicketRevocado = new StompHeaders();
        cabTicketRevocado.add("X-Ws-Ticket", ticketAntesDeCerrar);
        assertFallaConexion(cabTicketRevocado);
    }

    // ---- autorización de canales y bloqueo de SEND ----

    @Test
    void autorizaCanalTiendaSoloSiTienePermisoOrdenesVerYBloqueaSendYCuentasAjenas() throws Exception {
        UUID gestorPedidos = crearUsuarioActivo(false);
        agregarPersonal(tiendaId, gestorPedidos, "GESTOR_PEDIDOS");
        UUID gestorCatalogo = crearUsuarioActivo(false);
        agregarPersonal(tiendaId, gestorCatalogo, "GESTOR_CATALOGO");
        UUID ajeno = crearUsuarioActivo(false);

        String canalPropio = "/topic/tienda/" + tiendaId + "/ordenes";

        // Propietario y GESTOR_PEDIDOS pueden suscribirse y reciben mensajes tras commit
        StompSession sesionProp = conectar(new WebSocketHttpHeaders(), cabecerasJwt(jwtPropietario), new CompletableFuture<>());
        StompSession sesionPedidos = conectar(new WebSocketHttpHeaders(), cabecerasJwt(tokenPara(gestorPedidos, "ped@prueba.test")), new CompletableFuture<>());
        BlockingQueue<JsonNode> colaProp = suscribir(sesionProp, canalPropio);
        BlockingQueue<JsonNode> colaPedidos = suscribir(sesionPedidos, canalPropio);

        // GESTOR_CATALOGO (sin ORDENES_VER), usuario ajeno y anónimo son rechazados
        assertFallaSuscripcion(cabecerasJwt(tokenPara(gestorCatalogo, "cat@prueba.test")), canalPropio);
        assertFallaSuscripcion(cabecerasJwt(tokenPara(ajeno, "ajeno@prueba.test")), canalPropio);
        assertFallaSuscripcion(new StompHeaders(), canalPropio);
        assertFallaSuscripcion(cabecerasJwt(jwtPropietario), "/topic/tienda/*");
        assertFallaSuscripcion(cabecerasJwt(jwtPropietario), "/topic/tienda/" + tiendaId + "/otro");

        // Un cliente no puede inyectar mensajes SEND directamente al broker
        CompletableFuture<Throwable> errorSend = new CompletableFuture<>();
        StompSession atacante = conectar(new WebSocketHttpHeaders(), cabecerasJwt(jwtPropietario), errorSend);
        atacante.send(canalPropio, Map.of("tipo", "ORDEN_PAGADA_FALSA"));
        assertThat(errorSend.get(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(colaProp.poll(300, TimeUnit.MILLISECONDS)).isNull();

        // Verificar que propietario y GESTOR_PEDIDOS siguen recibiendo eventos reales
        UUID ordenId = insertarOrden("#2001", "tok-" + sufijo() + "-real", "ABIERTA", "PENDIENTE", "NO_PREPARADO");
        publicarEnTransaccion(new OrdenCreadaEvento(tiendaId, ordenId, "#2001"));
        assertThat(colaProp.poll(5, TimeUnit.SECONDS).path("tipo").asText()).isEqualTo("ORDEN_CREADA");
        assertThat(colaPedidos.poll(5, TimeUnit.SECONDS).path("tipo").asText()).isEqualTo("ORDEN_CREADA");

        sesionProp.disconnect();
        sesionPedidos.disconnect();
    }

    @Test
    void cerrarSesionesImpideNuevasSuscripcionesEnSocketYaAbierto() throws Exception {
        CompletableFuture<Throwable> error = new CompletableFuture<>();
        StompSession sesion = conectar(new WebSocketHttpHeaders(), cabecerasJwt(jwtPropietario), error);

        Thread.sleep(5);
        postJson("/cuenta/cerrar-sesiones", null, jwtPropietario, null).andExpect(status().isOk());

        sesion.subscribe("/topic/tienda/" + tiendaId + "/ordenes", lectorJson(new LinkedBlockingQueue<>()));
        assertThat(error.get(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void entregaNotificacionesEnTiempoRealDeTodosLosEventosYNoEnRollback() throws Exception {
        String tokenOrden = "orden-tok-" + sufijo();
        UUID ordenId = insertarOrden("#1001", tokenOrden, "ABIERTA", "PENDIENTE", "NO_PREPARADO");

        // Comprador anónimo con token falso es rechazado
        assertFallaSuscripcion(new StompHeaders(), "/topic/ordenes/token-que-no-existe-" + sufijo());

        // Conectar comerciante (con ticket efímero) y comprador invitado (anónimo)
        String ticket = cuerpo(postJson(Rutas.AUTH_WS_TICKET, null, jwtPropietario, null).andExpect(status().isOk()))
                .path("datos").path("ticket").asText();
        StompHeaders cabTicket = new StompHeaders();
        cabTicket.add("X-Ws-Ticket", ticket);
        StompSession panel = conectar(new WebSocketHttpHeaders(), cabTicket, new CompletableFuture<>());
        StompSession comprador = conectar(new WebSocketHttpHeaders(), new StompHeaders(), new CompletableFuture<>());

        BlockingQueue<JsonNode> mensajesPanel = suscribir(panel, "/topic/tienda/" + tiendaId + "/ordenes");
        BlockingQueue<JsonNode> mensajesComprador = suscribir(comprador, "/topic/ordenes/" + tokenOrden);

        // Rollback no emite nada
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                transaccion.executeWithoutResult(st -> {
                    publicador.publishEvent(new OrdenCreadaEvento(tiendaId, ordenId, "#1001"));
                    throw new IllegalStateException("rollback simulado");
                }));
        assertThat(mensajesPanel.poll(300, TimeUnit.MILLISECONDS)).isNull();

        // 1. Orden creada → solo panel
        publicarEnTransaccion(new OrdenCreadaEvento(tiendaId, ordenId, "#1001"));
        JsonNode evCreada = mensajesPanel.poll(5, TimeUnit.SECONDS);
        assertThat(evCreada).isNotNull();
        assertThat(evCreada.path("tipo").asText()).isEqualTo("ORDEN_CREADA");
        assertThat(evCreada.path("numero_orden").asText()).isEqualTo("#1001");
        assertThat(evCreada.path("tienda_id").asText()).isEqualTo(tiendaId.toString());
        assertThat(mensajesComprador.poll(250, TimeUnit.MILLISECONDS)).isNull();

        // 2. Orden pagada → panel y comprador (sin orden_id ni tienda_id en el canal del comprador)
        jdbc.update("UPDATE \"Ordenes\" SET \"EstadoPago\" = 'PAGADO' WHERE \"Id\" = ?", ordenId);
        publicarEnTransaccion(new OrdenPagadaEvento(tiendaId, ordenId));
        JsonNode evPagadaPanel = mensajesPanel.poll(5, TimeUnit.SECONDS);
        JsonNode evPagadaComp = mensajesComprador.poll(5, TimeUnit.SECONDS);
        assertThat(evPagadaPanel.path("tipo").asText()).isEqualTo("ORDEN_PAGADA");
        assertThat(evPagadaPanel.path("estado_pago").asText()).isEqualTo("PAGADO");
        assertThat(evPagadaComp.path("tipo").asText()).isEqualTo("ORDEN_PAGADA");
        assertThat(evPagadaComp.path("estado_pago").asText()).isEqualTo("PAGADO");
        assertThat(evPagadaComp.has("tienda_id")).isFalse();
        assertThat(evPagadaComp.has("orden_id")).isFalse();

        // 3. Envío despachado y entregado → panel y comprador reciben guía y estado
        UUID envioId = insertarEnvio(ordenId, "Olva Courier", "OT-20261111-ABCDEF");
        jdbc.update("UPDATE \"Ordenes\" SET \"EstadoPreparacion\" = 'PREPARADO' WHERE \"Id\" = ?", ordenId);
        publicarEnTransaccion(new EnvioDespachadoEvento(tiendaId, envioId, ordenId));
        JsonNode evDespPanel = mensajesPanel.poll(5, TimeUnit.SECONDS);
        JsonNode evDespComp = mensajesComprador.poll(5, TimeUnit.SECONDS);
        assertThat(evDespPanel.path("tipo").asText()).isEqualTo("ENVIO_DESPACHADO");
        assertThat(evDespPanel.path("numero_guia").asText()).isEqualTo("OT-20261111-ABCDEF");
        assertThat(evDespComp.path("tipo").asText()).isEqualTo("ENVIO_DESPACHADO");
        assertThat(evDespComp.path("transportista").asText()).isEqualTo("Olva Courier");
        assertThat(evDespComp.path("url_seguimiento").asText()).isEqualTo("https://seguimiento.test/OT-20261111-ABCDEF");

        jdbc.update("UPDATE \"Ordenes\" SET \"EstadoPreparacion\" = 'ENTREGADO', \"Estado\" = 'CERRADA' WHERE \"Id\" = ?", ordenId);
        publicarEnTransaccion(new EnvioEntregadoEvento(tiendaId, envioId, ordenId));
        JsonNode evEntPanel = mensajesPanel.poll(5, TimeUnit.SECONDS);
        JsonNode evEntComp = mensajesComprador.poll(5, TimeUnit.SECONDS);
        assertThat(evEntPanel.path("tipo").asText()).isEqualTo("ENVIO_ENTREGADO");
        assertThat(evEntComp.path("estado").asText()).isEqualTo("CERRADA");
        assertThat(evEntComp.path("estado_preparacion").asText()).isEqualTo("ENTREGADO");

        // 4. Orden cancelada → panel y comprador con motivo
        jdbc.update("UPDATE \"Ordenes\" SET \"Estado\" = 'CANCELADA', \"EstadoPago\" = 'ANULADO' WHERE \"Id\" = ?", ordenId);
        publicarEnTransaccion(new OrdenCanceladaEvento(tiendaId, ordenId, "CLIENTE"));
        JsonNode evCanPanel = mensajesPanel.poll(5, TimeUnit.SECONDS);
        JsonNode evCanComp = mensajesComprador.poll(5, TimeUnit.SECONDS);
        assertThat(evCanPanel.path("motivo").asText()).isEqualTo("CLIENTE");
        assertThat(evCanComp.path("tipo").asText()).isEqualTo("ORDEN_CANCELADA");
        assertThat(evCanComp.path("estado").asText()).isEqualTo("CANCELADA");

        panel.disconnect();
        comprador.disconnect();
    }

    // ---- ayudas STOMP y BD ----

    private String urlWs() {
        return "ws://localhost:" + puerto + "/api/v1/ws";
    }

    private WebSocketStompClient crearClienteStomp() {
        WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter convertidor = new MappingJackson2MessageConverter(json);
        cliente.setMessageConverter(convertidor);
        return cliente;
    }

    private StompSession conectar(
            WebSocketHttpHeaders httpHeaders,
            StompHeaders stompHeaders,
            CompletableFuture<Throwable> capturaError) throws Exception {
        WebSocketStompClient cliente = crearClienteStomp();
        return cliente.connectAsync(urlWs(), httpHeaders, stompHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void handleException(
                    @NonNull StompSession sesion,
                    @Nullable StompCommand comando,
                    @NonNull StompHeaders cabeceras,
                    @NonNull byte[] payload,
                    @NonNull Throwable ex) {
                capturaError.complete(ex);
            }

            @Override
            public void handleTransportError(@NonNull StompSession sesion, @NonNull Throwable ex) {
                capturaError.complete(ex);
            }
        }).get(5, TimeUnit.SECONDS);
    }

    private void assertFallaConexion(StompHeaders stompHeaders) {
        assertThatThrownBy(() -> conectar(new WebSocketHttpHeaders(), stompHeaders, new CompletableFuture<>()))
                .isInstanceOf(Exception.class);
    }

    private void assertFallaSuscripcion(StompHeaders stompHeaders, String destino) throws Exception {
        CompletableFuture<Throwable> error = new CompletableFuture<>();
        StompSession sesion = conectar(new WebSocketHttpHeaders(), stompHeaders, error);
        sesion.subscribe(destino, lectorJson(new LinkedBlockingQueue<>()));
        assertThat(error.get(5, TimeUnit.SECONDS)).isNotNull();
    }

    private BlockingQueue<JsonNode> suscribir(StompSession sesion, String destino) throws Exception {
        BlockingQueue<JsonNode> cola = new LinkedBlockingQueue<>();
        sesion.subscribe(destino, lectorJson(cola));
        // Breve pausa para asegurar el registro del SUBSCRIBE en el broker antes de emitir eventos
        Thread.sleep(60);
        return cola;
    }

    private StompFrameHandler lectorJson(BlockingQueue<JsonNode> cola) {
        return new StompFrameHandler() {
            @Override
            public @NonNull Type getPayloadType(@NonNull StompHeaders cabeceras) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders cabeceras, @Nullable Object payload) {
                if (payload instanceof JsonNode nodo) {
                    cola.offer(nodo);
                }
            }
        };
    }

    private StompHeaders cabecerasJwt(String jwt) {
        StompHeaders cab = new StompHeaders();
        cab.add("Authorization", "Bearer " + jwt);
        return cab;
    }

    private void publicarEnTransaccion(Object evento) {
        transaccion.executeWithoutResult(estado -> publicador.publishEvent(evento));
    }

    private UUID insertarOrden(String numero, String token, String estado, String estadoPago, String estadoPrep) {
        return jdbc.queryForObject(
                "INSERT INTO \"Ordenes\" (\"TiendaId\", \"NumeroOrden\", \"CorreoCliente\", \"NombreCliente\", "
                        + "\"Subtotal\", \"Total\", \"Estado\", \"EstadoPago\", \"EstadoPreparacion\", \"TokenAcceso\") "
                        + "VALUES (?, ?, 'comprador@prueba.test', 'Comprador', 50.00, 50.00, ?, ?, ?, ?) RETURNING \"Id\"",
                UUID.class, tiendaId, numero, estado, estadoPago, estadoPrep, token);
    }

    private UUID insertarEnvio(UUID ordenId, String transportista, String guia) {
        return jdbc.queryForObject(
                "INSERT INTO \"Envios\" (\"OrdenId\", \"EmpresaTransporte\", \"NumeroGuia\", \"UrlSeguimiento\", \"Estado\") "
                        + "VALUES (?, ?, ?, ?, 'EN_TRANSITO') RETURNING \"Id\"",
                UUID.class, ordenId, transportista, guia, "https://seguimiento.test/" + guia);
    }
}
