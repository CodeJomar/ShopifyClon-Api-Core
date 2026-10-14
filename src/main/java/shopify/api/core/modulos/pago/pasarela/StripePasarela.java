package shopify.api.core.modulos.pago.pasarela;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;

/**
 * Adaptador de Stripe (PAG-001, modo prueba con {@code sk_test_…}). Sin clave configurada, cualquier intento de pago
 * en línea responde 409 PAG-002 y el webhook responde 403 PAG-003.
 */
@Component
public class StripePasarela implements PasarelaEnLinea {

    private final String secretKey;
    private final String webhookSecret;

    public StripePasarela(@Value("${stripe.secret-key:}") String secretKey,
                          @Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public IntentoPago crearIntento(UUID ordenId, UUID tiendaId, long montoCentavos, String moneda) {
        exigirClave();
        try {
            PaymentIntent intento = PaymentIntent.create(PaymentIntentCreateParams.builder()
                    .setAmount(montoCentavos)
                    .setCurrency(moneda.toLowerCase(Locale.ROOT))
                    .putMetadata("orden_id", ordenId.toString())
                    .putMetadata("tienda_id", tiendaId.toString())
                    .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true)
                            .build())
                    .build(), opciones());
            return new IntentoPago(intento.getId(), intento.getClientSecret(), intento.getStatus());
        } catch (StripeException ex) {
            throw new ConflictoEstadoException("PAG-002", "No pudimos iniciar el pago en línea. Intenta de nuevo");
        }
    }

    @Override
    public IntentoPago recuperarIntento(String intentoId) {
        exigirClave();
        try {
            PaymentIntent intento = PaymentIntent.retrieve(intentoId, opciones());
            return new IntentoPago(intento.getId(), intento.getClientSecret(), intento.getStatus());
        } catch (StripeException ex) {
            throw new ConflictoEstadoException("PAG-002", "No pudimos recuperar el pago en línea. Intenta de nuevo");
        }
    }

    /**
     * Reembolso por el monto exacto en centavos. Solo {@code succeeded} cuenta como aprobado; un estado pendiente o
     * cualquier fallo de red se devuelve como no aprobado, y el servicio lo registra como RECHAZADO (PAG-007).
     */
    @Override
    public ResultadoReembolso reembolsar(String intentoId, long montoCentavos) {
        exigirClave();
        try {
            Refund reembolso = Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(intentoId)
                    .setAmount(montoCentavos)
                    .build(), opciones());
            return new ResultadoReembolso(reembolso.getId(), "succeeded".equals(reembolso.getStatus()));
        } catch (StripeException ex) {
            return new ResultadoReembolso(null, false);
        }
    }

    @Override
    public EventoPasarela verificarWebhook(String cuerpo, String firma) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new AccesoDenegadoException("PAG-003", "El webhook de pagos no está configurado");
        }
        Event evento;
        try {
            evento = Webhook.constructEvent(cuerpo, firma == null ? "" : firma, webhookSecret);
        } catch (SignatureVerificationException ex) {
            throw new AccesoDenegadoException("PAG-003", "Firma de webhook inválida");
        }
        Optional<StripeObject> objeto = evento.getDataObjectDeserializer().getObject();
        if (objeto.isPresent() && objeto.get() instanceof PaymentIntent intento) {
            return new EventoPasarela(evento.getType(), intento.getId(), intento.getAmount());
        }
        return new EventoPasarela(evento.getType(), null, 0L);
    }

    private void exigirClave() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ConflictoEstadoException("PAG-002", "Los pagos en línea no están configurados");
        }
    }

    private RequestOptions opciones() {
        return RequestOptions.builder().setApiKey(secretKey).build();
    }
}
