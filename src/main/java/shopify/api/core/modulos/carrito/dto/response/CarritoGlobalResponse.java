package shopify.api.core.modulos.carrito.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse.Advertencia;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse.Cupon;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse.Item;
import shopify.api.core.modulos.envio.dto.response.TarifaVitrinaResponse;

/**
 * Carrito global (R-001, B4): las líneas de todas las tiendas agrupadas por tienda, con los montos ya calculados.
 * {@code moneda} es la única moneda del carrito (nula si está vacío). {@code costoEnvio} de cada tienda es nulo
 * salvo que se cotice con tarifas (el total entonces es idéntico al del checkout).
 */
public record CarritoGlobalResponse(
        String token,
        String moneda,
        int cantidadArticulos,
        List<TiendaCarrito> tiendas,
        BigDecimal totalGeneral) {

    public record TiendaDelCarrito(UUID id, String nombre, String subdominio) {
    }

    public record TiendaCarrito(
            TiendaDelCarrito tienda,
            String moneda,
            List<Item> lineas,
            Cupon cupon,
            BigDecimal subtotal,
            BigDecimal descuentoTotal,
            BigDecimal costoEnvio,
            BigDecimal impuestosTotal,
            BigDecimal total,
            boolean requiereEnvio,
            List<Advertencia> advertencias) {
    }

    /** Tarifas activas de una tienda del carrito, para que el comprador elija el envío de cada una. */
    public record TarifasDeTienda(TiendaDelCarrito tienda, List<TarifaVitrinaResponse> tarifas) {
    }
}
