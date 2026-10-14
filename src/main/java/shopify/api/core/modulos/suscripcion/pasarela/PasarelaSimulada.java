package shopify.api.core.modulos.suscripcion.pasarela;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Pasarela de prueba (Q-008): aprueba todo salvo el token {@code tok_rechazar}. Se cambia por Stripe sin tocar el servicio. */
@Component
public class PasarelaSimulada implements PasarelaSuscripcion {

    static final String TOKEN_RECHAZADO = "tok_rechazar";

    @Override
    public ResultadoCobro cobrar(UUID suscripcionId, BigDecimal monto, String moneda, String tokenPago) {
        return new ResultadoCobro(!TOKEN_RECHAZADO.equals(tokenPago), "sim_" + UUID.randomUUID());
    }
}
