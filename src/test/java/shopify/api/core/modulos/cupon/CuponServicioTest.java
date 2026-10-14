package shopify.api.core.modulos.cupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.cupon.modelo.CuponAplicable;
import shopify.api.core.modulos.cupon.modelo.TipoDescuento;
import shopify.api.core.modulos.cupon.servicio.CuponServicio;

class CuponServicioTest extends PruebaCuponBase {

    @Autowired
    private CuponServicio cupones;

    private final Instant pasado = Instant.now().minus(1, ChronoUnit.DAYS);

    @Test
    void consumirUsoConLimiteUnoSoloSeAceptaUnaVezYDevolverRepone() {
        UUID tienda = nuevaTienda();
        UUID cupon = insertarCupon(tienda, "UNICO", "MONTO_FIJO", importe("5"), 1, true, pasado, null);

        enTransaccionSinResultado(() -> cupones.consumirUso(cupon));
        assertThat(usos(cupon)).isEqualTo(1);

        assertThatThrownBy(() -> enTransaccionSinResultado(() -> cupones.consumirUso(cupon)))
                .isInstanceOf(ConflictoEstadoException.class)
                .extracting("codigo").isEqualTo("CHK-005");

        enTransaccionSinResultado(() -> cupones.devolverUso(cupon));
        assertThat(usos(cupon)).isZero();

        enTransaccionSinResultado(() -> cupones.devolverUso(cupon));
        assertThat(usos(cupon)).isZero();
    }

    @Test
    void consumirUsoFueraDeTransaccionFalla() {
        UUID cupon = insertarCupon(nuevaTienda(), "SINTX", "MONTO_FIJO", importe("5"), null, true, pasado, null);

        assertThatThrownBy(() -> cupones.consumirUso(cupon))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void evaluarAceptaElCodigoEnMinusculasYDaElMotivoCuandoNoAplica() {
        UUID tienda = nuevaTienda();
        insertarCupon(tienda, "BIENVENIDA", "PORCENTAJE", importe("10"), null, true, pasado, null);
        UUID conMinimo = insertarCupon(tienda, "MINIMO", "MONTO_FIJO", importe("5"), null, true, pasado, null);
        jdbc.update("UPDATE \"Cupones\" SET \"MontoMinimoCompra\" = 100 WHERE \"Id\" = ?", conMinimo);

        CuponAplicable aplicable = cupones.evaluar(tienda, "bienvenida", importe("50"));
        assertThat(aplicable.codigo()).isEqualTo("BIENVENIDA");
        assertThat(aplicable.tipo()).isEqualTo(TipoDescuento.PORCENTAJE);
        assertThat(aplicable.valor()).isEqualByComparingTo("10.00");

        assertThatThrownBy(() -> cupones.evaluar(tienda, "MINIMO", importe("99")))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("codigo").isEqualTo("CUP-003");
        assertThatThrownBy(() -> cupones.evaluar(tienda, "MINIMO", importe("99")))
                .hasMessage("Compra mínima 100.00");

        assertThatThrownBy(() -> cupones.evaluar(tienda, "NO-EXISTE", importe("50")))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("codigo").isEqualTo("CUP-003");
    }
}
