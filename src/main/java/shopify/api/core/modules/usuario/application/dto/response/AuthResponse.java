package shopify.api.core.modules.usuario.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AuthResponse {
    private String tokenAcceso;
    private String tipoToken;
    private long expiraEnMs;
    private UUID idUsuario;
    private String correoElectronico;
    private String nombreCompleto;
    private String rol;
}