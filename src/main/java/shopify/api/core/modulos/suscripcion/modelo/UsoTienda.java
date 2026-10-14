package shopify.api.core.modulos.suscripcion.modelo;

/** Uso actual de la tienda frente a los límites de su plan (SUS-008, SUS-011). */
public record UsoTienda(long productos, long personal) {
}
