package shopify.api.core.modules.usuario.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UsuarioResponse {
    private UUID id;
    private String correoElectronico;
    private String nombres;
    private String apellidos;
    private String telefono;
    private Boolean estaActivo;
    private String rol;
    private Instant fechaCreacion;
}