package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Datos de una orden para los correos: quién compró, cuánto, y el token para su enlace de estado. */
public record DatosNotificacionOrden(
        UUID tiendaId,
        String numeroOrden,
        String correoCliente,
        String nombreCliente,
        BigDecimal total,
        String tokenAcceso) {
}
