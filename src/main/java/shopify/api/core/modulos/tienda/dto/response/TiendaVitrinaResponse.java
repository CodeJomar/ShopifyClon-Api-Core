package shopify.api.core.modulos.tienda.dto.response;

/** Datos públicos de la tienda (TIE-009): nada del propietario, impuestos internos ni suscripción. */
public record TiendaVitrinaResponse(
        String nombre,
        String subdominio,
        String moneda,
        String correoContacto,
        boolean preciosIncluyenImpuesto) {
}
