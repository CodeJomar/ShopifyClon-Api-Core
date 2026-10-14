package shopify.api.core.modulos.cupon;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import shopify.api.core.modulos.cupon.entidad.CuponEntidad;
import shopify.api.core.modulos.cupon.modelo.TipoDescuento;
import shopify.api.core.modulos.cupon.modelo.ValidadorCupon;

/** Reglas puras de cupón (CUP-003, CUP-004, CUP-006), sin base de datos. */
class ValidadorCuponTest {

    private static final Instant AHORA = Instant.parse("2026-10-11T12:00:00Z");

    private static CuponEntidad cupon(TipoDescuento tipo, String valor) {
        CuponEntidad cupon = new CuponEntidad();
        cupon.setCodigo("PRUEBA");
        cupon.setTipoDescuento(tipo);
        cupon.setValorDescuento(new BigDecimal(valor));
        cupon.setEstaActivo(true);
        cupon.setFechaInicio(AHORA.minus(1, ChronoUnit.DAYS));
        cupon.setMontoMinimoCompra(BigDecimal.ZERO);
        cupon.setCantidadUsosActual(0);
        return cupon;
    }

    @Test
    void inactivoNoAplica() {
        CuponEntidad cupon = cupon(TipoDescuento.MONTO_FIJO, "5");
        cupon.setEstaActivo(false);

        assertThat(ValidadorCupon.motivoNoAplicable(cupon, new BigDecimal("50"), AHORA))
                .hasValue("El cupón no está activo");
    }

    @Test
    void noIniciadoNoAplicaYLoDice() {
        CuponEntidad cupon = cupon(TipoDescuento.MONTO_FIJO, "5");
        cupon.setFechaInicio(AHORA.plus(1, ChronoUnit.DAYS));

        assertThat(ValidadorCupon.motivoNoAplicable(cupon, new BigDecimal("50"), AHORA))
                .hasValue("El cupón empieza el 12/10/2026");
    }

    @Test
    void vencidoNoAplicaYLoDiceConLaFecha() {
        CuponEntidad cupon = cupon(TipoDescuento.MONTO_FIJO, "5");
        cupon.setFechaFin(Instant.parse("2026-10-10T12:00:00Z"));

        assertThat(ValidadorCupon.motivoNoAplicable(cupon, new BigDecimal("50"), AHORA))
                .hasValue("El cupón venció el 10/10/2026");
    }

    @Test
    void agotadoNoAplica() {
        CuponEntidad cupon = cupon(TipoDescuento.MONTO_FIJO, "5");
        cupon.setLimiteUsosTotal(1);
        cupon.setCantidadUsosActual(1);

        assertThat(ValidadorCupon.motivoNoAplicable(cupon, new BigDecimal("50"), AHORA))
                .hasValue("El cupón ya no tiene usos disponibles");
    }

    @Test
    void bajoElMinimoNoAplicaYDiceElMinimo() {
        CuponEntidad cupon = cupon(TipoDescuento.PORCENTAJE, "10");
        cupon.setMontoMinimoCompra(new BigDecimal("100"));

        assertThat(ValidadorCupon.motivoNoAplicable(cupon, new BigDecimal("99.99"), AHORA))
                .hasValue("Compra mínima 100.00");
    }

    @Test
    void cuponVigenteConUsosDisponiblesAplica() {
        CuponEntidad cupon = cupon(TipoDescuento.PORCENTAJE, "10");
        cupon.setLimiteUsosTotal(2);
        cupon.setCantidadUsosActual(1);
        cupon.setFechaFin(AHORA.plus(1, ChronoUnit.DAYS));

        assertThat(ValidadorCupon.motivoNoAplicable(cupon, new BigDecimal("100"), AHORA)).isEmpty();
    }

    @Test
    void porcentajeSobreElSubtotalRedondeaAPesos() {
        assertThat(ValidadorCupon.descuento(cupon(TipoDescuento.PORCENTAJE, "15"), new BigDecimal("200")))
                .isEqualByComparingTo("30.00");
        assertThat(ValidadorCupon.descuento(cupon(TipoDescuento.PORCENTAJE, "10"), new BigDecimal("33.33")))
                .isEqualByComparingTo("3.33");
    }

    @Test
    void montoFijoNoSuperaElSubtotal() {
        assertThat(ValidadorCupon.descuento(cupon(TipoDescuento.MONTO_FIJO, "50"), new BigDecimal("30")))
                .isEqualByComparingTo("30.00");
        assertThat(ValidadorCupon.descuento(cupon(TipoDescuento.MONTO_FIJO, "20"), new BigDecimal("100")))
                .isEqualByComparingTo("20.00");
    }

    @Test
    void envioGratisNoDaDescuentoSobreElSubtotal() {
        assertThat(ValidadorCupon.descuento(cupon(TipoDescuento.ENVIO_GRATIS, "0"), new BigDecimal("100")))
                .isEqualByComparingTo("0.00");
    }
}
