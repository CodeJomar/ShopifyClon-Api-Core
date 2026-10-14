package shopify.api.core.modulos.orden.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;

/** Orden del historial del comprador (ORD-012). Sin comisión: no es un dato del comprador. */
public record OrdenCuentaResponse(
        UUID id,
        String numeroOrden,
        Instant fechaCreacion,
        UUID tiendaId,
        String tiendaNombre,
        String tiendaSubdominio,
        BigDecimal total,
        String moneda,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion) {
}
