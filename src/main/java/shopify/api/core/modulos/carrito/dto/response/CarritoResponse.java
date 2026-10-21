package shopify.api.core.modulos.carrito.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Carrito recalculado con precios actuales (CAR-005, D-018). Las líneas que no se pueden vender salen con
 * advertencia CAR-006 y fuera de los totales. {@code costoEnvio} es nulo salvo que se pida una tarifa (A-003);
 * entonces {@code totalEstimado} incluye el envío y es idéntico al total del checkout.
 */
public record CarritoResponse(
        String token,
        String estado,
        String moneda,
        List<Item> items,
        Cupon cupon,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal costoEnvio,
        BigDecimal impuestosTotal,
        BigDecimal totalEstimado,
        boolean requiereEnvio,
        List<Advertencia> advertencias) {

    public record Item(
            UUID varianteId,
            UUID productoId,
            String tituloProducto,
            String tituloVariante,
            String urlImagen,
            BigDecimal precioUnitario,
            int cantidad,
            BigDecimal totalLinea,
            boolean disponible,
            boolean requiereEnvio) {
    }

    /** {@code descuento} es el estimado del cupón; 0.00 si no descuenta (no aplica o carrito sin líneas). */
    public record Cupon(String codigo, BigDecimal descuento) {
    }

    public record Advertencia(String codigo, String mensaje, UUID varianteId) {
    }
}
