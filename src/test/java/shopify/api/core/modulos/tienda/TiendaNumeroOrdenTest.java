package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Número correlativo de la orden (paso 8 del checkout) y la búsqueda de tienda sin filtro de operatividad. */
class TiendaNumeroOrdenTest extends PruebaIntegracionBase {

    @Autowired
    private TiendaServicio tiendas;

    @Autowired
    private TransactionTemplate transaccion;

    @Test
    void losNumerosSaleCorrelativosDesdeMil001() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));

        String primero = transaccion.execute(estado -> tiendas.tomarNumeroOrden(tienda));
        String segundo = transaccion.execute(estado -> tiendas.tomarNumeroOrden(tienda));

        assertThat(primero).isEqualTo("#1001");
        assertThat(segundo).isEqualTo("#1002");
    }

    @Test
    void siLaTransaccionRevierteElNumeroNoQuedaUnHueco() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));

        assertThatThrownBy(() -> transaccion.execute(estado -> {
            tiendas.tomarNumeroOrden(tienda);
            throw new IllegalStateException("el checkout falla después del paso 8");
        })).isInstanceOf(IllegalStateException.class);

        String siguiente = transaccion.execute(estado -> tiendas.tomarNumeroOrden(tienda));
        assertThat(siguiente).isEqualTo("#1001");
    }

    @Test
    void tomarNumeroFueraDeUnaTransaccionFalla() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));

        assertThatThrownBy(() -> tiendas.tomarNumeroOrden(tienda)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void unaTiendaInexistenteNoTieneNumero() {
        assertThatThrownBy(() -> transaccion.execute(estado -> tiendas.tomarNumeroOrden(UUID.randomUUID())))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void idPorSubdominioEncuentraUnaTiendaDesactivadaQueLaVitrinaNoEncuentra() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        String subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        jdbc.update("UPDATE \"Tiendas\" SET \"EstaActiva\" = FALSE WHERE \"Id\" = ?", tienda);

        assertThat(tiendas.idPorSubdominio(subdominio)).contains(tienda);
        assertThat(tiendas.idOperativaPorSubdominio(subdominio)).isEmpty();
    }
}
