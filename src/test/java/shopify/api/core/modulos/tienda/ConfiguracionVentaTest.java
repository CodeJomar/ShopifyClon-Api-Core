package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shopify.api.core.modulos.tienda.modelo.ConfiguracionVenta;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

class ConfiguracionVentaTest extends PruebaIntegracionBase {

    @Autowired
    private TiendaServicio tiendas;

    @Test
    void devuelveMonedaImpuestoYSiEstaIncluido() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));

        ConfiguracionVenta configuracion = tiendas.configuracionVenta(tienda);

        assertThat(configuracion.moneda()).isEqualTo("USD");
        assertThat(configuracion.tasaImpuesto()).isEqualByComparingTo("0.1800");
        assertThat(configuracion.preciosIncluyenImpuesto()).isTrue();
    }
}
