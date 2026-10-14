package shopify.api.core.modulos.tienda.modelo;

import java.time.Instant;
import java.util.UUID;

/** Miembro del personal de una tienda con su rol. */
public interface MiembroPersonalVista {

    UUID getUsuarioId();

    String getCorreoElectronico();

    String getNombres();

    String getApellidos();

    String getRol();

    Instant getDesde();
}
