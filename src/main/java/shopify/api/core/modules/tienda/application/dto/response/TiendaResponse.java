package shopify.api.core.modules.tienda.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TiendaResponse {
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