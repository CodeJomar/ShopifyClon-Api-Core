package shopify.api.core.modulos.totales.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import shopify.api.core.comun.util.Dinero;

/**
 * Única fuente de la fórmula de totales (docs/negocio/04-reglas-de-integridad.md, "Dinero y cantidades").
 * Cálculo puro: sin base de datos ni Spring. Todo con {@link Dinero}: escala 2, HALF_UP.
 */
public final class CalculadoraTotales {

    private static final int MAX_LINEAS = 50;
    private static final int MIN_CANTIDAD = 1;
    private static final int MAX_CANTIDAD = 99;
    private static final BigDecimal TASA_MAXIMA = new BigDecimal("0.50");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal UNO = BigDecimal.ONE;

    private CalculadoraTotales() {
    }

    public static TotalesCalculados calcular(List<LineaCalculo> lineas, DescuentoCalculo descuento,
                                             EnvioCalculo envio, ImpuestoCalculo impuesto) {
        validar(lineas, descuento, envio, impuesto);

        List<BigDecimal> totalesLinea = new ArrayList<>();
        BigDecimal subtotal = Dinero.CERO;
        boolean requiereEnvio = false;
        for (LineaCalculo linea : lineas) {
            BigDecimal totalLinea = Dinero.multiplicar(linea.precioUnitario(), linea.cantidad());
            totalesLinea.add(totalLinea);
            subtotal = subtotal.add(totalLinea);
            requiereEnvio = requiereEnvio || linea.requiereEnvio();
        }

        BigDecimal descuentoTotal = calcularDescuento(descuento, subtotal);
        BigDecimal base = subtotal.subtract(descuentoTotal);
        BigDecimal costoEnvio = calcularEnvio(requiereEnvio, envio, descuento, base);
        boolean envioPendiente = requiereEnvio && envio == null;
        BigDecimal impuestos = calcularImpuestos(base, impuesto);
        BigDecimal total = impuesto.incluidoEnPrecio()
                ? base.add(costoEnvio)
                : base.add(costoEnvio).add(impuestos);

        return new TotalesCalculados(List.copyOf(totalesLinea), subtotal, descuentoTotal, costoEnvio, impuestos,
                total, requiereEnvio, envioPendiente);
    }

    private static BigDecimal calcularDescuento(DescuentoCalculo descuento, BigDecimal subtotal) {
        if (descuento == null) {
            return Dinero.CERO;
        }
        return switch (descuento.tipo()) {
            case PORCENTAJE -> Dinero.redondear(subtotal.multiply(descuento.valor()).divide(CIEN));
            case MONTO_FIJO -> Dinero.redondear(descuento.valor().min(subtotal));
            case ENVIO_GRATIS -> Dinero.CERO;
        };
    }

    private static BigDecimal calcularEnvio(boolean requiereEnvio, EnvioCalculo envio, DescuentoCalculo descuento,
                                            BigDecimal base) {
        if (!requiereEnvio || envio == null) {
            return Dinero.CERO;
        }
        if (descuento != null && descuento.tipo() == TipoDescuentoAplicado.ENVIO_GRATIS) {
            return Dinero.CERO;
        }
        if (envio.montoMinimoGratis() != null && base.compareTo(envio.montoMinimoGratis()) >= 0) {
            return Dinero.CERO;
        }
        return Dinero.redondear(envio.precio());
    }

    /** El envío no lleva impuesto (D-012): el impuesto se calcula solo sobre la base. */
    private static BigDecimal calcularImpuestos(BigDecimal base, ImpuestoCalculo impuesto) {
        if (impuesto.incluidoEnPrecio()) {
            return base.subtract(base.divide(UNO.add(impuesto.tasa()), Dinero.ESCALA, RoundingMode.HALF_UP));
        }
        return Dinero.porcentaje(base, impuesto.tasa());
    }

    private static void validar(List<LineaCalculo> lineas, DescuentoCalculo descuento, EnvioCalculo envio,
                                ImpuestoCalculo impuesto) {
        if (lineas == null || lineas.isEmpty() || lineas.size() > MAX_LINEAS) {
            throw new IllegalArgumentException("La orden debe tener de 1 a 50 líneas");
        }
        for (LineaCalculo linea : lineas) {
            if (linea == null || linea.precioUnitario() == null) {
                throw new IllegalArgumentException("Cada línea necesita un precio");
            }
            if (linea.precioUnitario().signum() < 0) {
                throw new IllegalArgumentException("El precio no puede ser negativo");
            }
            if (linea.cantidad() < MIN_CANTIDAD || linea.cantidad() > MAX_CANTIDAD) {
                throw new IllegalArgumentException("La cantidad debe estar entre 1 y 99");
            }
        }
        if (impuesto == null || impuesto.tasa() == null
                || impuesto.tasa().signum() < 0 || impuesto.tasa().compareTo(TASA_MAXIMA) > 0) {
            throw new IllegalArgumentException("La tasa de impuesto debe estar entre 0 y 0.50");
        }
        if (descuento != null && (descuento.tipo() == null || descuento.valor() == null
                || descuento.valor().signum() < 0)) {
            throw new IllegalArgumentException("El descuento necesita tipo y un valor no negativo");
        }
        if (envio != null && (envio.precio() == null || envio.precio().signum() < 0)) {
            throw new IllegalArgumentException("El precio del envío no puede ser negativo");
        }
    }
}
