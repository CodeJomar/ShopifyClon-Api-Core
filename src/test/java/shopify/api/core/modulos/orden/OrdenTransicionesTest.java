package shopify.api.core.modulos.orden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.ExcepcionNegocio;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Cada transición de {@link OrdenServicio}: el caso válido, los inválidos y el bloqueo por tienda. */
class OrdenTransicionesTest extends PruebaIntegracionBase {

    @Autowired
    private OrdenServicio ordenes;

    @Autowired
    private TransactionTemplate transaccion;

    private UUID tienda;

    @BeforeEach
    void preparar() {
        tienda = crearTienda(crearUsuarioActivo(false));
    }

    // ---- aplicarPagoConfirmado ----

    @Test
    void pagoConfirmadoConEnvioDejaLaOrdenPagadaYNoPreparada() {
        UUID orden = orden("ABIERTA", "PENDIENTE", "NO_PREPARADO", true);

        transaccion.executeWithoutResult(estado -> ordenes.aplicarPagoConfirmado(orden, new BigDecimal("2.00")));

        assertEstados(orden, "ABIERTA", "PAGADO", "NO_PREPARADO");
        assertThat(comision(orden)).isEqualByComparingTo("2.00");
    }

    @Test
    void pagoConfirmadoDeOrdenDigitalLaEntregaYLaCierra() {
        UUID orden = orden("ABIERTA", "PENDIENTE", "NO_PREPARADO", false);

        transaccion.executeWithoutResult(estado -> ordenes.aplicarPagoConfirmado(orden, BigDecimal.ZERO));

        assertEstados(orden, "CERRADA", "PAGADO", "ENTREGADO");
        assertThat(jdbc.queryForObject("SELECT \"FechaCierre\" FROM \"Ordenes\" WHERE \"Id\" = ?",
                java.sql.Timestamp.class, orden)).isNotNull();
    }

    @Test
    void pagoConfirmadoSobreOrdenAnuladaEsORD005() {
        UUID orden = orden("CANCELADA", "ANULADO", "NO_PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(
                estado -> ordenes.aplicarPagoConfirmado(orden, BigDecimal.ZERO)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-005"));
        assertEstados(orden, "CANCELADA", "ANULADO", "NO_PREPARADO");
    }

    @Test
    void pagoConfirmadoSobreOrdenYaPagadaEsORD005() {
        UUID orden = orden("ABIERTA", "PAGADO", "NO_PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(
                estado -> ordenes.aplicarPagoConfirmado(orden, BigDecimal.ZERO)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-005"));
    }

    // ---- anular ----

    @Test
    void anularOrdenSinPagarLaCancelaYAnulaElPago() {
        UUID orden = orden("ABIERTA", "PENDIENTE", "NO_PREPARADO", true);

        transaccion.executeWithoutResult(estado -> ordenes.anular(orden, "CLIENTE", "Cambió de opinión"));

        assertEstados(orden, "CANCELADA", "ANULADO", "NO_PREPARADO");
        assertThat(jdbc.queryForObject("SELECT \"MotivoCancelacion\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, orden))
                .isEqualTo("CLIENTE");
        assertThat(jdbc.queryForObject("SELECT \"FechaCancelacion\" FROM \"Ordenes\" WHERE \"Id\" = ?",
                java.sql.Timestamp.class, orden)).isNotNull();
    }

    @Test
    void anularOrdenPagadaEsORD007() {
        UUID orden = orden("ABIERTA", "PAGADO", "NO_PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> ordenes.anular(orden, "CLIENTE", null)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-007"));
        assertEstados(orden, "ABIERTA", "PAGADO", "NO_PREPARADO");
    }

    @Test
    void anularSinMotivoEsVAL001() {
        UUID orden = orden("ABIERTA", "PENDIENTE", "NO_PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> ordenes.anular(orden, " ", null)))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("VAL-001"));
        assertEstados(orden, "ABIERTA", "PENDIENTE", "NO_PREPARADO");
    }

    // ---- marcarPreparada / marcarEntregada ----

    @Test
    void preparaUnaOrdenPagadaYLaEntregaYLaCierra() {
        UUID orden = orden("ABIERTA", "PAGADO", "NO_PREPARADO", true);

        transaccion.executeWithoutResult(estado -> ordenes.marcarPreparada(orden));
        assertEstados(orden, "ABIERTA", "PAGADO", "PREPARADO");

        transaccion.executeWithoutResult(estado -> ordenes.marcarEntregada(orden));
        assertEstados(orden, "CERRADA", "PAGADO", "ENTREGADO");
    }

    @Test
    void prepararSinPagoEsORD006() {
        UUID orden = orden("ABIERTA", "PENDIENTE", "NO_PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> ordenes.marcarPreparada(orden)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-006"));
        assertEstados(orden, "ABIERTA", "PENDIENTE", "NO_PREPARADO");
    }

    @Test
    void prepararDosVecesEsORD006() {
        UUID orden = orden("ABIERTA", "PAGADO", "PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> ordenes.marcarPreparada(orden)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-006"));
    }

    @Test
    void entregarUnaOrdenNoPreparadaEsORD006() {
        UUID orden = orden("ABIERTA", "PAGADO", "NO_PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> ordenes.marcarEntregada(orden)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-006"));
        assertEstados(orden, "ABIERTA", "PAGADO", "NO_PREPARADO");
    }

    // ---- aplicarReembolso ----

    @Test
    void reembolsoParcialYLuegoTotalSigueElAcumulado() {
        UUID orden = orden("ABIERTA", "PAGADO", "NO_PREPARADO", true);

        transaccion.executeWithoutResult(estado -> ordenes.aplicarReembolso(orden, false));
        assertEstados(orden, "ABIERTA", "PARCIALMENTE_REEMBOLSADO", "NO_PREPARADO");

        transaccion.executeWithoutResult(estado -> ordenes.aplicarReembolso(orden, false));
        assertEstados(orden, "ABIERTA", "PARCIALMENTE_REEMBOLSADO", "NO_PREPARADO");

        transaccion.executeWithoutResult(estado -> ordenes.aplicarReembolso(orden, true));
        assertEstados(orden, "ABIERTA", "REEMBOLSADO", "NO_PREPARADO");
    }

    @Test
    void reembolsarSobrePagoPendienteEsORD005() {
        UUID orden = orden("ABIERTA", "PENDIENTE", "NO_PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> ordenes.aplicarReembolso(orden, true)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-005"));
        assertEstados(orden, "ABIERTA", "PENDIENTE", "NO_PREPARADO");
    }

    @Test
    void reembolsarUnaOrdenYaReembolsadaEsORD005() {
        UUID orden = orden("ABIERTA", "REEMBOLSADO", "NO_PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> ordenes.aplicarReembolso(orden, false)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-005"));
    }

    // ---- cancelarPagada ----

    @Test
    void cancelarPagadaSinPrepararLaCancela() {
        UUID orden = orden("ABIERTA", "REEMBOLSADO", "NO_PREPARADO", true);

        transaccion.executeWithoutResult(estado -> ordenes.cancelarPagada(orden, "SIN_STOCK", "Reembolso completo"));

        assertEstados(orden, "CANCELADA", "REEMBOLSADO", "NO_PREPARADO");
    }

    @Test
    void cancelarPagadaYaPreparadaEsORD007() {
        UUID orden = orden("ABIERTA", "PAGADO", "PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(
                estado -> ordenes.cancelarPagada(orden, "CLIENTE", null)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-007"));
        assertEstados(orden, "ABIERTA", "PAGADO", "PREPARADO");
    }

    @Test
    void cancelarOrdenSinCobrarPorEsteMetodoEsORD007() {
        UUID orden = orden("ABIERTA", "PENDIENTE", "NO_PREPARADO", true);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(
                estado -> ordenes.cancelarPagada(orden, "CLIENTE", null)))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-007"));
    }

    // ---- bloqueo y pertenencia a la tienda ----

    @Test
    void bloquearDevuelveEstadosYLineasDeLaOrden() {
        UUID orden = orden("ABIERTA", "PENDIENTE", "NO_PREPARADO", true);

        OrdenEstado bloqueada = transaccion.execute(estado -> ordenes.bloquear(tienda, orden));

        assertThat(bloqueada.id()).isEqualTo(orden);
        assertThat(bloqueada.estadoPago().name()).isEqualTo("PENDIENTE");
        assertThat(bloqueada.items()).hasSize(1);
        assertThat(bloqueada.requiereEnvio()).isTrue();
    }

    @Test
    void bloquearOrdenDeOtraTiendaEsORD404() {
        UUID orden = orden("ABIERTA", "PENDIENTE", "NO_PREPARADO", true);
        UUID otra = crearTienda(crearUsuarioActivo(false));

        assertThatThrownBy(() -> transaccion.execute(estado -> ordenes.bloquear(otra, orden)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-404"));
    }

    @Test
    void unaTransicionSobreOrdenInexistenteEsORD404() {
        assertThatThrownBy(() -> transaccion.executeWithoutResult(
                estado -> ordenes.marcarPreparada(UUID.randomUUID())))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("ORD-404"));
    }

    // ---- ayudas ----

    /** Orden con una línea (variante real) en el estado indicado. */
    private UUID orden(String estado, String pago, String preparacion, boolean conEnvio) {
        UUID orden = jdbc.queryForObject("INSERT INTO \"Ordenes\" (\"TiendaId\", \"NumeroOrden\", \"CorreoCliente\", "
                + "\"NombreCliente\", \"Subtotal\", \"Total\", \"Estado\", \"EstadoPago\", \"EstadoPreparacion\") "
                + "VALUES (?, ?, 'c@prueba.test', 'Comprador', 0, 0, ?, ?, ?) RETURNING \"Id\"", UUID.class,
                tienda, "#" + sufijo(), estado, pago, preparacion);
        jdbc.update("INSERT INTO \"ItemsOrden\" (\"OrdenId\", \"VarianteId\", \"TituloProducto\", \"TituloVariante\", "
                + "\"Cantidad\", \"PrecioUnitario\", \"TotalLinea\", \"RequiereEnvio\") VALUES (?, ?, 'Producto', 'Por defecto', "
                + "1, 0, 0, ?)", orden, variante(), conEnvio);
        return orden;
    }

    private UUID variante() {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\") "
                + "VALUES (?, 'Producto', ?) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        return jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\") "
                + "VALUES (?, 'Por defecto', ?, 10.00) RETURNING \"Id\"", UUID.class, producto, "SKU-" + sufijo());
    }

    private void assertEstados(UUID orden, String estado, String pago, String preparacion) {
        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, orden))
                .isEqualTo(estado);
        assertThat(jdbc.queryForObject("SELECT \"EstadoPago\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, orden))
                .isEqualTo(pago);
        assertThat(jdbc.queryForObject("SELECT \"EstadoPreparacion\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, orden))
                .isEqualTo(preparacion);
    }

    private BigDecimal comision(UUID orden) {
        return jdbc.queryForObject("SELECT \"ComisionPlataforma\" FROM \"Ordenes\" WHERE \"Id\" = ?", BigDecimal.class, orden);
    }
}
