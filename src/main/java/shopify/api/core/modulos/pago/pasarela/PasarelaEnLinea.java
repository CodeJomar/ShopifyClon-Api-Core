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

    /** Lee un intento existente (para devolver el mismo {@code client_secret} al reintentar). */
    IntentoPago recuperarIntento(String intentoId);

    /** Reembolsa un cargo por el monto exacto en centavos. Un rechazo o un fallo de red devuelve aprobado = false. */
    ResultadoReembolso reembolsar(String intentoId, long montoCentavos);

    /** Verifica la firma del webhook y devuelve el evento. Firma inválida → 403 PAG-003. */
    EventoPasarela verificarWebhook(String cuerpo, String firma);
}
