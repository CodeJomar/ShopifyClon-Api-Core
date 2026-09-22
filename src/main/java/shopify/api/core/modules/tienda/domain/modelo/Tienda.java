package shopify.api.core.modules.tienda.domain.modelo;

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
public class Tienda {
    private UUID id;
    private UUID propietarioId;
    private String nombre;
    private String subdominio;
    private String dominioPersonalizado;
    private String moneda;
    private String correoContacto;
    private Boolean estaActiva;
    private Instant fechaCreacion;
}