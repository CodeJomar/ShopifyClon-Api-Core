package shopify.api.core.modulos.usuario.modelo;

import java.util.UUID;

/** Proyección de solo lectura de las tiendas en las que participa un usuario. */
public interface TiendaDeUsuarioVista {

    UUID getTiendaId();

    String getNombre();

    String getSubdominio();

    String getRol();
}
