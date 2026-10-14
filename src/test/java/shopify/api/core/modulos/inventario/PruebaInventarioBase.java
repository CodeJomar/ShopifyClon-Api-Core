package shopify.api.core.modulos.inventario;

import java.util.UUID;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Variantes con su inventario preparado en la base, y lecturas directas de cantidades y movimientos. */
abstract class PruebaInventarioBase extends PruebaIntegracionBase {

    /** Producto y variante de la tienda con su fila de inventario; devuelve el id de la variante. */
    protected UUID variante(UUID tiendaId, boolean rastrea, int disponible, int reservada, boolean ventaSinStock) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\") "
                + "VALUES (?, 'Producto', ?) RETURNING \"Id\"", UUID.class, tiendaId, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\") "
                + "VALUES (?, 'Por defecto', ?, 10.00) RETURNING \"Id\"", UUID.class, producto, "SKU-" + sufijo());
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, ?, ?, ?, ?)",
                variante, disponible, reservada, rastrea, ventaSinStock);
        return variante;
    }

    protected int disponible(UUID varianteId) {
        return jdbc.queryForObject("SELECT \"CantidadDisponible\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, varianteId);
    }

    protected int reservada(UUID varianteId) {
        return jdbc.queryForObject("SELECT \"CantidadReservada\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, varianteId);
    }

    protected int movimientos(UUID varianteId, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM \"MovimientosInventario\" WHERE \"VarianteId\" = ? AND \"Tipo\" = ?",
                Integer.class, varianteId, tipo);
    }

    protected int movimientosTotales(UUID varianteId) {
        return jdbc.queryForObject("SELECT count(*) FROM \"MovimientosInventario\" WHERE \"VarianteId\" = ?",
                Integer.class, varianteId);
    }

    protected int auditoria(UUID tiendaId, String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? AND \"Accion\" = ?",
                Integer.class, tiendaId, accion);
    }
}
