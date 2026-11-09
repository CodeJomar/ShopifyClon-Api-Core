package shopify.api.core.modulos.ruta.modelo;

import java.util.UUID;

/** Paradas totales y entregadas de una ruta, para el listado. */
public interface ConteoRutaVista {

    UUID getRutaId();

    Long getTotal();

    Long getEntregadas();
}
