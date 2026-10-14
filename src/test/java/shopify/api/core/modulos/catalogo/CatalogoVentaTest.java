package shopify.api.core.modulos.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shopify.api.core.modulos.catalogo.modelo.VarianteVendible;
import shopify.api.core.modulos.catalogo.servicio.CatalogoVentaServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Precios y vendibilidad de venta (carrito y checkout). Sin Spring MVC: son métodos de servicio. */
class CatalogoVentaTest extends PruebaIntegracionBase {

    @Autowired
    private CatalogoVentaServicio catalogoVenta;

    private UUID producto(UUID tiendaId, boolean publicado, boolean eliminado) {
        return jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", \"EstaPublicado\", "
                + "\"Eliminado\") VALUES (?, 'Remera', ?, ?, ?) RETURNING \"Id\"", UUID.class,
                tiendaId, "r-" + sufijo(), publicado, eliminado);
    }

    private UUID variante(UUID productoId, String precio, boolean eliminada) {
        return jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\", "
                + "\"Eliminado\") VALUES (?, 'M', ?, ?, ?) RETURNING \"Id\"", UUID.class,
                productoId, "V-" + sufijo(), new java.math.BigDecimal(precio), eliminada);
    }

    @Test
    void marcaVendibleSoloLaVarianteViva() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID publicado = producto(tienda, true, false);
        UUID viva = variante(publicado, "20.00", false);
        UUID eliminada = variante(publicado, "25.00", true);
        UUID sinPublicar = variante(producto(tienda, false, false), "30.00", false);

        var mapa = catalogoVenta.variantesParaVenta(tienda, List.of(viva, eliminada, sinPublicar));

        assertThat(mapa.get(viva).vendible()).isTrue();
        assertThat(mapa.get(viva).precio()).isEqualByComparingTo("20.00");
        assertThat(mapa.get(eliminada).vendible()).isFalse();
        assertThat(mapa.get(sinPublicar).vendible()).isFalse();
    }

    @Test
    void unaVarianteDeOtraTiendaNoAparece() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID ajena = variante(producto(crearTienda(crearUsuarioActivo(false)), true, false), "10.00", false);

        VarianteVendible vista = catalogoVenta.variantesParaVenta(tienda, List.of(ajena)).get(ajena);
        assertThat(vista).isNull();
    }

    @Test
    void sinIdsDevuelveMapaVacio() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        assertThat(catalogoVenta.variantesParaVenta(tienda, List.of())).isEmpty();
    }
}
