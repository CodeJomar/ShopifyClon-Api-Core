package shopify.api.core.modulos.pago.pasarela;

import java.util.UUID;

/**
 * Puerto de la pasarela en línea (PAG-001, PAG-002, PAG-003). La implementación real es {@link StripePasarela}; en
 * pruebas la reemplaza una pasarela falsa. Ninguna llamada aquí se hace con transacciones de base de datos abiertas
 * (PAG-010).
 */
public interface PasarelaEnLinea {

    /** Crea un intento de cobro por el monto exacto en centavos. */
    IntentoPago crearIntento(UUID ordenId, UUID tiendaId, long montoCentavos, String moneda);

    /**
     * Crea un solo intento de cobro por el total de un pedido (varias órdenes, una por tienda). Por defecto se comporta
     * como un intento común con el id del pedido; Stripe lo marca con {@code pedido_id} en los metadatos.
     */
    default IntentoPago crearIntentoPedido(UUID pedidoId, long montoCentavos, String moneda) {
        return crearIntento(pedidoId, pedidoId, montoCentavos, moneda);
    }

    /** Lee un intento existente (para devolver el mismo {@code client_secret} al reintentar). */
    IntentoPago recuperarIntento(String intentoId);

    /** Reembolsa un cargo por el monto exacto en centavos. Un rechazo o un fallo de red devuelve aprobado = false. */
    ResultadoReembolso reembolsar(String intentoId, long montoCentavos);

    /** Verifica la firma del webhook y devuelve el evento. Firma inválida → 403 PAG-003. */
    EventoPasarela verificarWebhook(String cuerpo, String firma);
}
