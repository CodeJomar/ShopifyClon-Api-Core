package shopify.api.core.modulos.tienda.modelo;

import java.util.UUID;

/** Datos públicos mínimos de una tienda operativa (carrito global). */
public record TiendaPublica(UUID id, String nombre, String subdominio, String moneda) {
}
