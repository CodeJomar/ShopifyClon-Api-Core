package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Datos de un pedido nuevo, con los totales ya sumados de sus órdenes hijas. */
public record NuevoPedido(
        UUID usuarioId,
        String correoCliente,
        String nombreCliente,
        String telefonoCliente,
        String moneda,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal costoEnvio,
        BigDecimal impuestosTotal,
        BigDecimal total,
        PasarelaPago pasarela,
        UUID claveIdempotencia,
        String tokenAcceso,
        Instant fechaExpiracionPago) {
}
