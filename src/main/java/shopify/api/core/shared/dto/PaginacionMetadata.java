package shopify.api.core.shared.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class PaginacionMetadata {
    private int paginaActual;
    private int tamanoPagina;
    private long totalElementos;
    private int totalPaginas;
    private boolean esPrimera;
    private boolean esUltima;

    public static <T> PaginacionMetadata desdePagina(Page<T> pagina) {
        return PaginacionMetadata.builder()
                .paginaActual(pagina.getNumber())
                .tamanoPagina(pagina.getSize())
                .totalElementos(pagina.getTotalElements())
                .totalPaginas(pagina.getTotalPages())
                .esPrimera(pagina.isFirst())
                .esUltima(pagina.isLast())
                .build();
    }
}