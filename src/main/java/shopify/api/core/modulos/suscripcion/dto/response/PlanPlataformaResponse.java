package shopify.api.core.modulos.suscripcion.dto.response;

import java.math.BigDecimal;

/** Plan tal como lo administra la plataforma: incluye la visibilidad y el orden. */
public record PlanPlataformaResponse(
        String codigo,
        String nombre,
        String descripcion,
        BigDecimal precioMensual,
        BigDecimal precioAnual,
        String moneda,
        Integer limiteProductos,
        Integer limitePersonal,
        BigDecimal porcentajeComision,
        Integer diasPrueba,
        Boolean esPublico,
        Integer orden) {
}
