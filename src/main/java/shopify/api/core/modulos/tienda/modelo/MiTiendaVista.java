package shopify.api.core.modulos.tienda.modelo;

import java.util.UUID;

/** Proyección de solo lectura de una tienda en la que participa el usuario. */
public interface MiTiendaVista {

    UUID getTiendaId();

    String getNombre();

    String getSubdominio();

    String getRol();

    Boolean getEstaActiva();
}
