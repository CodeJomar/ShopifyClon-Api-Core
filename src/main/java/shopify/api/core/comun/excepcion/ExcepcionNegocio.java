package shopify.api.core.comun.excepcion;

import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base de toda excepción de negocio: lleva un código estable (por ejemplo {@code CAT-003}),
 * el estado HTTP que le corresponde y detalles opcionales para la persona o el cliente.
 */
@Getter
public abstract class ExcepcionNegocio extends RuntimeException {

    private final String codigo;
    private final HttpStatus estado;
    private final Map<String, Object> detalles;

    protected ExcepcionNegocio(String codigo, HttpStatus estado, String mensaje, Map<String, Object> detalles) {
        super(mensaje);
        this.codigo = codigo;
        this.estado = estado;
        this.detalles = detalles == null ? Collections.emptyMap() : Map.copyOf(detalles);
    }
}
