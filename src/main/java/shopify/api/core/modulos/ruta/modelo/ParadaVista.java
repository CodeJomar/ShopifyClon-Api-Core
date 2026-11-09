package shopify.api.core.modulos.ruta.modelo;

import java.util.UUID;

/** Parada con los datos de su envío y su orden, leídos en una sola consulta para armar la guía de remisión. */
public interface ParadaVista {

    Integer getPosicion();

    UUID getEnvioId();

    UUID getOrdenId();

    String getNumeroOrden();

    String getNumeroGuia();

    String getEstadoEnvio();

    String getNombreCliente();

    String getTelefonoCliente();

    /** Snapshot de la dirección de envío de la orden, como texto JSON. */
    String getDireccion();
}
