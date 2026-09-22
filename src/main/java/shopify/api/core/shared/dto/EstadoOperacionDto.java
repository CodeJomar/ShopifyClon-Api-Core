package shopify.api.core.shared.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class EstadoOperacionDto {

    private boolean exito;
    private int codigoEstado;
    private String mensaje;
    private UUID id;         // ID del registro creado, editado o eliminado
    private String codigo;     // Código comercial o SKU generado (ej: "ORD-1001", "PROD-01")
    private Instant marcaTiempo;

    public static EstadoOperacionDto creado(UUID id, String codigo, String mensaje) {
        return EstadoOperacionDto.builder()
                .exito(true)
                .codigoEstado(201)
                .mensaje(mensaje)
                .id(id)
                .codigo(codigo)
                .marcaTiempo(Instant.now())
                .build();
    }

    public static EstadoOperacionDto exitoso(UUID id, String mensaje) {
        return EstadoOperacionDto.builder()
                .exito(true)
                .codigoEstado(200)
                .mensaje(mensaje)
                .id(id)
                .marcaTiempo(Instant.now())
                .build();
    }
}