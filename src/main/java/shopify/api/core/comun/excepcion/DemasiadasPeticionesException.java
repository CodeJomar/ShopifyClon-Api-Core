package shopify.api.core.comun.excepcion;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 429: se superó el límite de peticiones; indica cuántos segundos faltan para reintentar. */
@Getter
public class DemasiadasPeticionesException extends ExcepcionNegocio {

    private final long segundosRestantes;

    public DemasiadasPeticionesException(long segundosRestantes) {
        super("GEN-429", HttpStatus.TOO_MANY_REQUESTS,
                "Demasiados intentos. Espera un momento e inténtalo de nuevo",
                Map.of("reintentar_en_segundos", segundosRestantes));
        this.segundosRestantes = segundosRestantes;
    }
}
