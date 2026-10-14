package shopify.api.core.modulos.carrito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.carrito.modelo.CarritoParaCheckout;
import shopify.api.core.modulos.carrito.modelo.EstadoCarrito;
import shopify.api.core.modulos.carrito.servicio.CarritoCheckoutServicio;
import shopify.api.core.modulos.carrito.servicio.CarritoServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Operaciones del carrito que usa el checkout: bloqueo, orden ligada y conversión. */
class CarritoCheckoutServicioTest extends PruebaIntegracionBase {

    @Autowired
    private CarritoServicio carritos;

    @Autowired
    private CarritoCheckoutServicio checkout;

    @Autowired
    private TransactionTemplate transaccion;

    private UUID tienda;
    private String subdominio;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    @Test
    void bloqueaElCarritoYDevuelveSusLineasSinPrecios() {
        String token = carritos.crear(subdominio).token();
        UUID variante = variante(new BigDecimal("25.00"));
        carritos.agregar(subdominio, token, variante, 3);

        CarritoParaCheckout bloqueado = transaccion.execute(estado -> checkout.bloquearParaCheckout(tienda, token));

        assertThat(bloqueado.estado()).isEqualTo(EstadoCarrito.ACTIVO);
        assertThat(bloqueado.ordenId()).isNull();
        assertThat(bloqueado.lineas()).hasSize(1);
        assertThat(bloqueado.lineas().get(0).varianteId()).isEqualTo(variante);
        assertThat(bloqueado.lineas().get(0).cantidad()).isEqualTo(3);
    }

    @Test
    void unTokenQueNoEsDeLaTiendaEsCarrito404() {
        String token = carritos.crear(subdominio).token();
        UUID otra = crearTiendaOperativa(crearUsuarioActivo(false));

        assertThatThrownBy(() -> transaccion.execute(estado -> checkout.bloquearParaCheckout(otra, token)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("No encontramos ese carrito");
    }

    @Test
    void bloquearFueraDeUnaTransaccionFalla() {
        String token = carritos.crear(subdominio).token();

        assertThatThrownBy(() -> checkout.bloquearParaCheckout(tienda, token))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void unCarritoSinOrdenDevuelveVacioYLuegoQuedaConvertidoConSuOrden() {
        String token = carritos.crear(subdominio).token();
        assertThat(checkout.ordenDe(tienda, token)).isEmpty();

        UUID carritoId = transaccion.execute(estado -> checkout.bloquearParaCheckout(tienda, token).carritoId());
        // Carritos.OrdenId tiene clave foránea a Ordenes: la orden tiene que existir de verdad
        UUID orden = jdbc.queryForObject("INSERT INTO \"Ordenes\" (\"TiendaId\", \"NumeroOrden\", \"CorreoCliente\", "
                + "\"NombreCliente\", \"Subtotal\", \"Total\") VALUES (?, '#9001', 'c@prueba.test', 'Comprador', 0, 0) "
                + "RETURNING \"Id\"", UUID.class, tienda);
        transaccion.executeWithoutResult(estado -> checkout.marcarConvertido(carritoId, orden));

        assertThat(checkout.ordenDe(tienda, token)).contains(orden);
        CarritoParaCheckout despues = transaccion.execute(estado -> checkout.bloquearParaCheckout(tienda, token));
        assertThat(despues.estado()).isEqualTo(EstadoCarrito.CONVERTIDO);
        assertThat(despues.ordenId()).isEqualTo(orden);
    }

    @Test
    void ordenDeConUnTokenAjenoEsCarrito404() {
        String token = carritos.crear(subdominio).token();
        UUID otra = crearTiendaOperativa(crearUsuarioActivo(false));

        assertThatThrownBy(() -> checkout.ordenDe(otra, token)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    /** Variante vendible de la tienda con su inventario (el carrito la exige para agregarla). */
    private UUID variante(BigDecimal precio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\") "
                + "VALUES (?, 'Por defecto', ?, ?) RETURNING \"Id\"", UUID.class, producto, "SKU-" + sufijo(), precio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 50, 0, TRUE, FALSE)", variante);
        return variante;
    }
}
