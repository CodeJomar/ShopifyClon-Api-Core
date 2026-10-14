package shopify.api.core.modulos.cupon.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import shopify.api.core.modulos.cupon.modelo.TipoDescuento;

public record CuponResponse(
        UUID id,
        String codigo,
        TipoDescuento tipoDescuento,
        BigDecimal valorDescuento,
        BigDecimal montoMinimoCompra,
        Integer limiteUsosTotal,
        Integer cantidadUsosActual,
        Instant fechaInicio,
        Instant fechaFin,
        Boolean estaActivo) {
}
