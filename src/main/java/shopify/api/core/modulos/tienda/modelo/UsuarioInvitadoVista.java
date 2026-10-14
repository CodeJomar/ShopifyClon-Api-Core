package shopify.api.core.modulos.tienda.modelo;

import java.util.UUID;

/** Datos mínimos de un usuario activo al que se puede agregar al personal. */
public interface UsuarioInvitadoVista {

    UUID getId();

    String getCorreoElectronico();

    String getNombres();

    String getApellidos();
}
