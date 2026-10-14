package shopify.api.core.modulos.pago.pasarela;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;

/**
 * Pasarela de pruebas: sustituye a Stripe en todo el contexto de test. No hay red. La firma del webhook es un HMAC
 * con un secreto fijo; {@link #firmar} la calcula igual que lo haría el emisor.
 */
@Primary
@Component
public class PasarelaEnLineaFalsa implements PasarelaEnLinea {

    public static final String SECRETO = "secreto-de-prueba";

    private final ObjectMapper json = new ObjectMapper();

    /** Firma que espera la falsa para un cuerpo dado. */
    public static String firmar(String cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public IntentoPago crearIntento(UUID ordenId, UUID tiendaId, long montoCentavos, String moneda) {
        String id = "pi_test_" + UUID.randomUUID().toString().replace("-", "");
        return new IntentoPago(id, "pi_test_secret_" + id, "requires_payment_method");
    }

    @Override
    public IntentoPago recuperarIntento(String intentoId) {
        return new IntentoPago(intentoId, "pi_test_secret_" + intentoId, "requires_payment_method");
    }

    @Override
    public EventoPasarela verificarWebhook(String cuerpo, String firma) {
        if (firma == null || !firma.equals(firmar(cuerpo))) {
            throw new AccesoDenegadoException("PAG-003", "Firma de webhook inválida");
        }
        try {
            JsonNode raiz = json.readTree(cuerpo);
            JsonNode objeto = raiz.path("data").path("object");
            String intentoId = objeto.hasNonNull("id") ? objeto.get("id").asText() : null;
            long monto = objeto.path("amount").asLong(0L);
            return new EventoPasarela(raiz.path("type").asText(), intentoId, monto);
        } catch (Exception ex) {
            throw new AccesoDenegadoException("PAG-003", "Cuerpo de webhook inválido");
        }
    }
}
