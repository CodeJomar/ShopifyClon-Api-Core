package shopify.api.core.shared.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ConsultaPaginadaDto<T> {

    private List<T> datos;
    private long total;
    private int paginaActual;
    private int tamanoPagina;
    private int totalPaginas;
    private boolean tieneMasPaginas;
    private Instant marcaTiempo;

    public static <T> ConsultaPaginadaDto<T> desdePagina(Page<T> pagina) {
        return ConsultaPaginadaDto.<T>builder()
                .datos(pagina.getContent())
                .total(pagina.getTotalElements())
                .paginaActual(pagina.getNumber())
                .tamanoPagina(pagina.getSize())
                .totalPaginas(pagina.getTotalPages())
                .tieneMasPaginas(pagina.hasNext())
                .marcaTiempo(Instant.now())
                .build();
    }
}