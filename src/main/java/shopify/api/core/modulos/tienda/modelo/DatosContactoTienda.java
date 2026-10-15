package shopify.api.core.modulos.tienda.modelo;

import java.util.UUID;

/** Datos de la tienda que necesitan los correos: nombre, subdominio, moneda y a quién avisar. */
public record DatosContactoTienda(
        UUID tiendaId,
        String nombre,
        String subdominio,
        String moneda,
        String correoContacto,
        String correoPropietario) {
}
