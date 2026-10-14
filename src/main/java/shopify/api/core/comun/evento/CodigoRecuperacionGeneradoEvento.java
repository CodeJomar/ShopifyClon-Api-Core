package shopify.api.core.comun.evento;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CodigoRecuperacionGeneradoEvento {
    private UUID idUsuario;
    private String correoElectronico;
    private String nombres;
    private String codigoOtp;
}