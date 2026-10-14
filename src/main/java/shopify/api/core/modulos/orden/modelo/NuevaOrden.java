package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Datos completos de una orden nueva, ya calculados y validados por quien orquesta (checkout). Todos los importes
 * vienen de {@code CalculadoraTotales}; este módulo solo los guarda.
 */
public record NuevaOrden(
        UUID tiendaId,
        UUID usuarioId,
        String numeroOrden,
        String correoCliente,
        String nombreCliente,
        String telefonoCliente,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal costoEnvio,
        BigDecimal impuestosTotal,
        BigDecimal total,
        UUID cuponId,
        String codigoCupon,
        String nombreTarifaEnvio,
        PasarelaPago pasarela,
        BigDecimal tasaImpuesto,
        boolean preciosIncluyenImpuesto,
        UUID claveIdempotencia,
        String tokenAcceso,
        Instant fechaExpiracionPago,
        Map<String, Object> direccionEnvio,
        Map<String, Object> direccionFacturacion,
        List<NuevaLinea> lineas) {
}
