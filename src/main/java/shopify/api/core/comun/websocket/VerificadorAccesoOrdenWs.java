package shopify.api.core.comun.websocket;

/** Contrato transversal para autorizar la suscripción al canal público de una orden sin acoplar {@code comun} a {@code orden}. */
public interface VerificadorAccesoOrdenWs {

    boolean existeOrdenActivaPorTokenAcceso(String tokenAcceso);
}
