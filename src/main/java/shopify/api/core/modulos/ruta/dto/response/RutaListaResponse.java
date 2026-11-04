package shopify.api.core.modulos.ruta.dto.response;

import java.time.LocalDate;
import java.util.UUID;
import shopify.api.core.modulos.ruta.modelo.EstadoRuta;

/** Elemento del listado de rutas. */
public record RutaListaResponse(
        UUID id,
        String codigo,
        LocalDate fecha,
        String chofer,
        String placa,
        EstadoRuta estado,
        long totalParadas,
        long paradasEntregadas) {
}
