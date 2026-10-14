package shopify.api.core.modulos.pago.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modulos.pago.servicio.WebhookStripeServicio;

/**
 * Webhook público con firma válida (PAG-003). Recibe el cuerpo crudo: la firma se calcula sobre los bytes exactos.
 */
@Tag(name = "Webhooks: pagos", description = "Notificaciones de la pasarela de pago")
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookStripeControlador {

    private final WebhookStripeServicio webhook;

    @Operation(summary = "Recibir un evento de Stripe (firma en Stripe-Signature)")
    @PostMapping("/stripe")
    public ResponseEntity<Void> recibir(@RequestBody String cuerpo,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String firma) {
        webhook.procesar(cuerpo, firma);
        return ResponseEntity.ok().build();
    }
}
