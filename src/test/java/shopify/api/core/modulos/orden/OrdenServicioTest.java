package shopify.api.core.modulos.orden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.NuevaLinea;
import shopify.api.core.modulos.orden.modelo.NuevaOrden;
import shopify.api.core.modulos.orden.modelo.OrdenResumen;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Guardas de {@code Ordenes} e {@code ItemsOrden}: el servicio escribe y la base rechaza lo que no cuadra. */
class OrdenServicioTest extends PruebaIntegracionBase {

    @Autowired
    private OrdenServicio ordenes;

    @Autowired
    private TransactionTemplate transaccion;

    private UUID tienda;

    @BeforeEach
    void preparar() {
        tienda = crearTienda(crearUsuarioActivo(false));
    }

    @Test
    void creaLaOrdenConSusLineasYNaceAbiertaPendienteYNoPreparada() {
        UUID clave = UUID.randomUUID();
        NuevaLinea linea = linea("Camiseta", "20.00", 2, "40.00");
        // Subtotal 40.00 − descuento 4.00 + envío 5.00 = 41.00 (impuesto incluido en el precio)
        transaccion.execute(estado -> ordenes.crear(
                nueva(clave, "#1001", List.of(linea), "40.00", "4.00", "5.00", "41.00")));

        OrdenResumen resumen = ordenes.buscarPorClave(tienda, clave).orElseThrow();
        assertThat(resumen.numeroOrden()).isEqualTo("#1001");
        assertThat(resumen.estado()).isEqualTo(EstadoOrden.ABIERTA);
        assertThat(resumen.estadoPago()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(resumen.estadoPreparacion()).isEqualTo(EstadoPreparacion.NO_PREPARADO);
        assertThat(resumen.pasarela()).isEqualTo(PasarelaPago.MANUAL);
        assertThat(resumen.total()).isEqualByComparingTo("41.00");
        assertThat(resumen.items()).hasSize(1);
        assertThat(resumen.items().get(0).tituloProducto()).isEqualTo("Camiseta");
        assertThat(resumen.items().get(0).totalLinea()).isEqualByComparingTo("40.00");
    }

    @Test
    void crearFueraDeUnaTransaccionFalla() {
        NuevaOrden datos = nueva(UUID.randomUUID(), "#1002", List.of(linea("Gorra", "10.00", 1, "10.00")),
                "10.00", "0.00", "0.00", "10.00");

        assertThatThrownBy(() -> ordenes.crear(datos)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void laMismaClaveNoPuedeCrearDosOrdenesEnLaMismaTienda() {
        UUID clave = UUID.randomUUID();
        transaccion.execute(estado -> ordenes.crear(nueva(clave, "#1003",
                List.of(linea("Gorra", "10.00", 1, "10.00")), "10.00", "0.00", "0.00", "10.00")));

        assertThatThrownBy(() -> transaccion.execute(estado -> ordenes.crear(nueva(clave, "#1004",
                List.of(linea("Gorra", "10.00", 1, "10.00")), "10.00", "0.00", "0.00", "10.00"))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UxOrdenesClaveIdempotencia");
    }

    @Test
    void laBaseRechazaUnTotalQueNoCuadraConLaFormula() {
        // Subtotal 10.00 y envío 0.00: el total debe ser 10.00, no 999.00 (ChkOrdenesFormulaTotal)
        NuevaOrden datos = nueva(UUID.randomUUID(), "#1005", List.of(linea("Gorra", "10.00", 1, "10.00")),
                "10.00", "0.00", "0.00", "999.00");

        assertThatThrownBy(() -> transaccion.execute(estado -> ordenes.crear(datos)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ChkOrdenesFormulaTotal");
    }

    @Test
    void laBaseRechazaUnaLineaCuyoTotalNoEsPrecioPorCantidad() {
        // La línea dice 15.00 pero 10.00 × 2 = 20.00. Subtotal y total de la orden son coherentes entre sí,
        // así que el único CHECK que falla es el de la línea (ChkItemsOrdenTotalLinea).
        NuevaOrden datos = nueva(UUID.randomUUID(), "#1006", List.of(linea("Gorra", "10.00", 2, "15.00")),
                "15.00", "0.00", "0.00", "15.00");

        assertThatThrownBy(() -> transaccion.execute(estado -> ordenes.crear(datos)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ChkItemsOrdenTotalLinea");
    }

    @Test
    void buscarPorClaveDeOtraTiendaNoEncuentraLaOrden() {
        UUID clave = UUID.randomUUID();
        transaccion.execute(estado -> ordenes.crear(nueva(clave, "#1007",
                List.of(linea("Gorra", "10.00", 1, "10.00")), "10.00", "0.00", "0.00", "10.00")));
        UUID otra = crearTienda(crearUsuarioActivo(false));

        assertThat(ordenes.buscarPorClave(otra, clave)).isEmpty();
    }

    private NuevaLinea linea(String titulo, String precio, int cantidad, String totalLinea) {
        return new NuevaLinea(variante(), titulo, "Talla M", "SKU-" + sufijo(), cantidad,
                new BigDecimal(precio), new BigDecimal(totalLinea), false);
    }

    /** ItemsOrden.VarianteId tiene clave foránea: la línea necesita una variante real de la tienda. */
    private UUID variante() {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\") "
                + "VALUES (?, 'Producto', ?) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        return jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\") "
                + "VALUES (?, 'Por defecto', ?, 10.00) RETURNING \"Id\"", UUID.class, producto, "SKU-" + sufijo());
    }

    private NuevaOrden nueva(UUID clave, String numero, List<NuevaLinea> lineas, String subtotal, String descuento,
                             String envio, String total) {
        return new NuevaOrden(tienda, null, numero, "comprador@prueba.test", "Comprador", null,
                new BigDecimal(subtotal), new BigDecimal(descuento), new BigDecimal(envio), BigDecimal.ZERO,
                new BigDecimal(total), null, null, null, PasarelaPago.MANUAL, new BigDecimal("0.1800"), true, clave,
                "tok-" + sufijo(), null, null, null, lineas);
    }
}
