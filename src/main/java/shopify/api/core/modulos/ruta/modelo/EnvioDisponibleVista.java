package shopify.api.core.modulos.ruta.modelo;

import java.util.UUID;

/** Envío creado o en tránsito que todavía no está en ninguna ruta activa. */
public interface EnvioDisponibleVista {

    UUID getEnvioId();

    String getNumeroOrden();

    String getNumeroGuia();

    String getEstadoEnvio();

    String getDestinatario();

    String getCiudad();

    String getDireccionLinea1();

    Long getTotalItems();
}
