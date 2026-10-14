package shopify.api.core.modulos.suscripcion.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Vista de la suscripción no cancelada de una tienda, con los límites de su plan. */
public record SuscripcionVigente(
        UUID suscripcionId,
        UUID tiendaId,
        String codigoPlan,
        EstadoSuscripcion estado,
        Instant finPeriodoActual,
        Integer limiteProductos,
        int limitePersonal,
        BigDecimal porcentajeComision) {

    public boolean permiteOperar() {
        return estado.permiteOperar();
    }
}
