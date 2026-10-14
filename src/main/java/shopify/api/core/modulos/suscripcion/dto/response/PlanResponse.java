package shopify.api.core.modulos.suscripcion.dto.response;

import java.math.BigDecimal;

/** Plan público. No expone id, visibilidad ni orden (suscripciones.md, planes semilla). */
public record PlanResponse(
        String codigo,
        String nombre,
        String descripcion,
        BigDecimal precioMensual,
        BigDecimal precioAnual,
        String moneda,
        Integer limiteProductos,
        Integer limitePersonal,
        BigDecimal porcentajeComision,
        Integer diasPrueba) {
}
