package shopify.api.core.shared.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ConsultaIndividualDto<T> {

    private boolean exito;
    private T datos;
    private Instant marcaTiempo;

    public static <T> ConsultaIndividualDto<T> de(T datos) {
        return ConsultaIndividualDto.<T>builder()
                .exito(true)
                .datos(datos)
                .marcaTiempo(Instant.now())
                .build();
    }
}