package shopify.api.core.modulos.ruta.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import shopify.api.core.modulos.ruta.modelo.EstadoRuta;

/** Ruta con todo lo necesario para pintar la hoja de ruta y la guía de remisión sin más llamadas. */
public record RutaDetalleResponse(
        UUID id,
        String codigo,
        LocalDate fecha,
        String chofer,
        String placa,
        EstadoRuta estado,
        String nota,
        Remitente tienda,
        List<Parada> paradas) {

    /** Remitente de la guía. Teléfono y dirección no se guardan en la tienda todavía: salen ausentes. */
    public record Remitente(String nombre, String correoElectronico, String telefono, String direccion) {
    }

    public record Parada(
            int posicion,
            UUID envioId,
            UUID ordenId,
            String numeroOrden,
            String numeroGuia,
            String estadoEnvio,
            String destinatario,
            String telefono,
            Direccion direccion,
            BigDecimal latitud,
            BigDecimal longitud,
            List<Item> items) {
    }

    public record Direccion(
            String direccionLinea1,
            String direccionLinea2,
            String ciudad,
            String estadoProvincia,
            String codigoPostal,
            String pais) {
    }

    public record Item(String tituloProducto, String tituloVariante, String sku, int cantidad) {
    }
}
