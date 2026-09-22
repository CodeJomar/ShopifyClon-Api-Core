package shopify.api.core.modules.catalogo.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CategoriaResponse {
    private UUID id;
    private UUID tiendaId;
    private String nombre;
    private String slug;
    private String descripcion;
    private String urlImagen;
    private Boolean estaActiva;
    private Instant fechaCreacion;
}