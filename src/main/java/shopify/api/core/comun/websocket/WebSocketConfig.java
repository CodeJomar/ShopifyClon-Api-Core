package shopify.api.core.comun.websocket;

import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import shopify.api.core.comun.util.OrigenesPermitidos;

/**
 * Broker STOMP sobre WebSocket nativo en {@code /api/v1/ws}.
 * Usa broker simple en memoria (válido para una réplica, como el limitador de peticiones) con heartbeats
 * periódicos para mantener vivas las conexiones tras proxies TLS como Railway.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AutenticacionStompInterceptor autenticacionStompInterceptor;
    private final List<String> origenesPermitidos;
    private final long heartbeatMs;
    private final ThreadPoolTaskScheduler planificadorHeartbeats;

    public WebSocketConfig(
            AutenticacionStompInterceptor autenticacionStompInterceptor,
            @Value("${app.cors.origenes-permitidos:http://localhost:3000}") String origenes,
            @Value("${app.websocket.heartbeat-ms:10000}") long heartbeatMs) {
        this.autenticacionStompInterceptor = autenticacionStompInterceptor;
        this.origenesPermitidos = OrigenesPermitidos.desdeCsv(origenes);
        this.heartbeatMs = Math.max(0L, heartbeatMs);
        // No se expone como @Bean para no interferir con los ejecutores que auto-configura Spring Boot para canales STOMP
        this.planificadorHeartbeats = new ThreadPoolTaskScheduler();
        this.planificadorHeartbeats.setPoolSize(1);
        this.planificadorHeartbeats.setThreadNamePrefix("ws-hb-");
        this.planificadorHeartbeats.setRemoveOnCancelPolicy(true);
        this.planificadorHeartbeats.initialize();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint("/ws")
                .setAllowedOrigins(origenesPermitidos.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        var broker = registro.enableSimpleBroker("/topic", "/queue");
        if (heartbeatMs > 0) {
            broker.setTaskScheduler(planificadorHeartbeats)
                    .setHeartbeatValue(new long[]{heartbeatMs, heartbeatMs});
        }
        registro.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(autenticacionStompInterceptor);
    }

    @PreDestroy
    void detenerPlanificador() {
        planificadorHeartbeats.shutdown();
    }
}
