package shopify.api.core.modulos.cupon.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.cupon.entidad.CuponEntidad;

/** Reglas puras de aplicabilidad y descuento de un cupón (CUP-003, CUP-004, CUP-006). */
public final class ValidadorCupon {

    // TODO(Q-T-010): usar la ZonaHoraria de la tienda; por ahora, el valor por defecto del esquema.
    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private ValidadorCupon() {
    }

    /** Motivo concreto por el que el cupón no aplica, o vacío si aplica (CUP-003, CUP-006). */
    public static Optional<String> motivoNoAplicable(CuponEntidad cupon, BigDecimal subtotal, Instant ahora) {
        if (!Boolean.TRUE.equals(cupon.getEstaActivo())) {
            return Optional.of("El cupón no está activo");
        }
        if (cupon.getFechaInicio().isAfter(ahora)) {
            return Optional.of("El cupón empieza el " + fecha(cupon.getFechaInicio()));
        }
        if (cupon.getFechaFin() != null && !cupon.getFechaFin().isAfter(ahora)) {
            return Optional.of("El cupón venció el " + fecha(cupon.getFechaFin()));
        }
        if (cupon.getLimiteUsosTotal() != null && cupon.getCantidadUsosActual() >= cupon.getLimiteUsosTotal()) {
            return Optional.of("El cupón ya no tiene usos disponibles");
        }
        BigDecimal minimo = cupon.getMontoMinimoCompra() == null ? BigDecimal.ZERO : cupon.getMontoMinimoCompra();
        if (subtotal.compareTo(minimo) < 0) {
            return Optional.of("Compra mínima " + Dinero.redondear(minimo).toPlainString());
        }
        return Optional.empty();
    }

    /** Descuento sobre el subtotal, redondeado a 2 decimales; nunca supera el subtotal (CUP-004). */
    public static BigDecimal descuento(CuponEntidad cupon, BigDecimal subtotal) {
        return switch (cupon.getTipoDescuento()) {
            case PORCENTAJE -> Dinero.redondear(subtotal.multiply(cupon.getValorDescuento()).divide(CIEN));
            case MONTO_FIJO -> Dinero.redondear(cupon.getValorDescuento().min(subtotal));
            case ENVIO_GRATIS -> Dinero.CERO;
        };
    }

    private static String fecha(Instant instante) {
        return FECHA.format(instante.atZone(ZONA));
    }
}
