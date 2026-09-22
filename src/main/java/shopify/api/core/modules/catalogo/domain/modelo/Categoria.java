package shopify.api.core.modules.catalogo.domain.modelo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Categoria {
    private UUID id;
    private UUID tiendaId;
    private String nombre;
    private String slug;
    private String descripcion;
    private String urlImagen;
    private Boolean estaActiva;
    private Instant fechaCreacion;
}