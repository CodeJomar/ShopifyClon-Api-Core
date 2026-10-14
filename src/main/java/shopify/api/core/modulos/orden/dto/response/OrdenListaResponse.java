package shopify.api.core.modulos.orden.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;

/** Fila del listado del panel (ORD-011). */
public record OrdenListaResponse(
        UUID id,
        String numeroOrden,
        Instant fechaCreacion,
        String nombreCliente,
        String correoCliente,
        BigDecimal total,
        String moneda,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion) {
}
