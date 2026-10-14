package shopify.api.core.modulos.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.modulos.inventario.modelo.Disponibilidad;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

class InventarioTest extends PruebaIntegracionBase {

    @Autowired
    private InventarioServicio inventario;

    @Autowired
    private TransactionTemplate transaccion;

    private UUID nuevaVariante() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\") "
                + "VALUES (?, 'Producto', ?) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        return jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\") "
                + "VALUES (?, 'Por defecto', ?, 10.00) RETURNING \"Id\"", UUID.class, producto, "SKU-" + sufijo());
    }

    /** Variante con una fila de inventario; devuelve su id. */
    private UUID varianteConInventario(boolean rastrea, int disponible, int reservado, boolean ventaSinStock) {
        UUID variante = nuevaVariante();
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, ?, ?, ?, ?)",
                variante, disponible, reservado, rastrea, ventaSinStock);
        return variante;
    }

    private int movimientos(UUID varianteId, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM \"MovimientosInventario\" WHERE \"VarianteId\" = ? AND \"Tipo\" = ?",
                Integer.class, varianteId, tipo);
    }

    @Test
    void crearInicialConCantidadEscribeInventarioYUnMovimientoInicial() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID variante = nuevaVariante();

        transaccion.executeWithoutResult(estado -> inventario.crearInicial(tienda, variante, 5, true, false));

        Map<String, Object> fila = jdbc.queryForMap("SELECT \"DisponibleResultante\", \"ReservadoResultante\" "
                + "FROM \"MovimientosInventario\" WHERE \"VarianteId\" = ?", variante);
        assertThat(fila.get("DisponibleResultante")).isEqualTo(5);
        assertThat(fila.get("ReservadoResultante")).isEqualTo(0);
        assertThat(movimientos(variante, "INICIAL")).isEqualTo(1);
        assertThat(inventario.de(variante)).hasValueSatisfying(d -> assertThat(d.vendible()).isEqualTo(5));
    }

    @Test
    void crearInicialConCeroNoEscribeMovimiento() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID variante = nuevaVariante();

        transaccion.executeWithoutResult(estado -> inventario.crearInicial(tienda, variante, 0, true, false));

        assertThat(movimientos(variante, "INICIAL")).isZero();
        assertThat(inventario.de(variante)).isPresent();
    }

    @Test
    void crearInicialFueraDeTransaccionFalla() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID variante = nuevaVariante();

        assertThatThrownBy(() -> inventario.crearInicial(tienda, variante, 5, true, false))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void disponibilidadSigueINV001ParaCadaCaso() {
        UUID sinRastreo = varianteConInventario(false, 0, 0, false);
        UUID conStock = varianteConInventario(true, 5, 3, false);
        UUID agotado = varianteConInventario(true, 5, 5, false);
        UUID sinStockPermitida = varianteConInventario(true, 0, 0, true);

        Map<UUID, Disponibilidad> mapa = inventario.disponibilidad(
                List.of(sinRastreo, conStock, agotado, sinStockPermitida));

        assertThat(mapa.get(sinRastreo).disponible()).isTrue();
        assertThat(mapa.get(conStock).vendible()).isEqualTo(2);
        assertThat(mapa.get(conStock).disponible()).isTrue();
        assertThat(mapa.get(agotado).vendible()).isZero();
        assertThat(mapa.get(agotado).disponible()).isFalse();
        assertThat(mapa.get(sinStockPermitida).disponible()).isTrue();
        assertThat(mapa.get(sinStockPermitida).ventaSinStock()).isTrue();
    }

    @Test
    void varianteSinFilaDeInventarioNoAparece() {
        UUID sinFila = nuevaVariante();

        assertThat(inventario.disponibilidad(List.of(sinFila))).doesNotContainKey(sinFila);
        assertThat(inventario.de(sinFila)).isEmpty();
    }
}
