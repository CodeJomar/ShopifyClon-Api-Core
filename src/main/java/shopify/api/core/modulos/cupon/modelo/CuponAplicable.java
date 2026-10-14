package shopify.api.core.modulos.cupon.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Cupón que sí aplica a un subtotal, listo para que carrito o checkout calculen el descuento (CUP-003). */
public record CuponAplicable(UUID id, String codigo, TipoDescuento tipo, BigDecimal valor) {
}
