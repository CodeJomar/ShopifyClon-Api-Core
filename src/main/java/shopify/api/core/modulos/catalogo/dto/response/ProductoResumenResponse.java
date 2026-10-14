package shopify.api.core.modulos.catalogo.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Fila del listado del panel: precio mínimo, número de variantes, stock vendible e imagen principal. */
public record ProductoResumenResponse(
        UUID id,
        String titulo,
        String identificadorUrl,
        Boolean estaPublicado,
        BigDecimal precioMinimo,
        int numeroVariantes,
        long stockVendibleTotal,
        String imagenPrincipal) {
}
