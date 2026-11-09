package shopify.api.core.modulos.ruta.modelo;

import java.util.UUID;

/** Estado y tienda de un envío que se quiere agregar a una ruta (RUT-001). */
public interface EnvioParaRutaVista {

    UUID getEnvioId();

    String getEstado();

    UUID getTiendaId();
}
