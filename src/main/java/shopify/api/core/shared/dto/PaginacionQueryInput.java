package shopify.api.core.shared.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaginacionQueryInput {

    @Min(value = 0, message = "La página no puede ser menor a 0")
    private int pagina = 0;

    @Min(value = 1, message = "El tamaño mínimo de página es 1")
    @Max(value = 100, message = "El tamaño máximo de página permitido es 100")
    private int tamano = 10;

    private String textoBusqueda;

    private String ordenarPor = "fechaCreacion";

    private String direccionOrden = "DESC"; // ASC o DESC
}