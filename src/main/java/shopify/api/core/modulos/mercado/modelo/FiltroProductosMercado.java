package shopify.api.core.modulos.mercado.modelo;

import java.math.BigDecimal;

/**
 * Filtros ya validados del listado de productos. {@code texto} viene normalizado (minúsculas, sin tildes) y con los
 * comodines de LIKE escapados; cualquier campo nulo no filtra.
 */
public record FiltroProductosMercado(
        String texto,
        String categoria,
        String tienda,
        BigDecimal precioMin,
        BigDecimal precioMax,
        OrdenProductos orden,
        int limite,
        int desplazamiento) {

    public enum OrdenProductos {
        RECIENTES("p.\"FechaCreacion\" DESC, p.\"Id\""),
        PRECIO_ASC("vmin.\"Precio\" ASC, p.\"Id\""),
        PRECIO_DESC("vmin.\"Precio\" DESC, p.\"Id\"");

        private final String sql;

        OrdenProductos(String sql) {
            this.sql = sql;
        }

        /** Fragmento ORDER BY fijo (nunca viene del cliente: se elige por enumeración). */
        public String sql() {
            return sql;
        }
    }
}
