package shopify.api.core.modulos.orden.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;

/** Detalle completo para el panel (ORD-011). Incluye la comisión: dato interno del comerciante. */
public record OrdenDetallePanelResponse(
        UUID id,
        String numeroOrden,
        Instant fechaCreacion,
        String nombreCliente,
        String correoCliente,
        String telefonoCliente,
        Map<String, Object> direccionEnvio,
        Map<String, Object> direccionFacturacion,
        List<ItemOrdenVistaResponse> items,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal costoEnvio,
        BigDecimal impuestosTotal,
        BigDecimal total,
        String moneda,
        String codigoCupon,
        String nombreTarifaEnvio,
        BigDecimal tasaImpuesto,
        Boolean preciosIncluyenImpuesto,
        PasarelaPago pasarela,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion,
        Instant fechaExpiracionPago,
        Instant fechaCancelacion,
        String motivoCancelacion,
        Instant fechaCierre,
        BigDecimal comisionPlataforma) {
}
