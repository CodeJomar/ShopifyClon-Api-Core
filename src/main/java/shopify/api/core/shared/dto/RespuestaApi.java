package shopify.api.core.shared.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RespuestaApi<T> {

    private boolean exito;
    private int codigoEstado;
    private String mensaje;
    private T datos;
    private PaginacionMetadata paginacion;
    private String error;
    private String ruta;
    private Map<String, String> validaciones;
    private Instant marcaTiempo;

    // --- MÉTODOS ESTÁTICOS DE ÉXITO ---

    public static <T> ResponseEntity<RespuestaApi<T>> ok(T datos, String mensaje) {
        RespuestaApi<T> respuesta = RespuestaApi.<T>builder()
                .exito(true)
                .codigoEstado(HttpStatus.OK.value())
                .mensaje(mensaje)
                .datos(datos)
                .marcaTiempo(Instant.now())
                .build();
        return ResponseEntity.ok(respuesta);
    }

    public static <T> ResponseEntity<RespuestaApi<T>> creado(T datos, String mensaje) {
        RespuestaApi<T> respuesta = RespuestaApi.<T>builder()
                .exito(true)
                .codigoEstado(HttpStatus.CREATED.value())
                .mensaje(mensaje)
                .datos(datos)
                .marcaTiempo(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    public static <T> ResponseEntity<RespuestaApi<List<T>>> paginado(Page<T> pagina, String mensaje) {
        RespuestaApi<List<T>> respuesta = RespuestaApi.<List<T>>builder()
                .exito(true)
                .codigoEstado(HttpStatus.OK.value())
                .mensaje(mensaje)
                .datos(pagina.getContent())
                .paginacion(PaginacionMetadata.desdePagina(pagina))
                .marcaTiempo(Instant.now())
                .build();
        return ResponseEntity.ok(respuesta);
    }

    // --- MÉTODO ESTÁTICO DE ERROR (Para el Manejador Global) ---

    public static ResponseEntity<RespuestaApi<Void>> error(
            HttpStatus estado, String mensaje, String error, String ruta, Map<String, String> validaciones) {

        RespuestaApi<Void> respuesta = RespuestaApi.<Void>builder()
                .exito(false)
                .codigoEstado(estado.value())
                .mensaje(mensaje)
                .error(error)
                .ruta(ruta)
                .validaciones(validaciones)
                .marcaTiempo(Instant.now())
                .build();
        return ResponseEntity.status(estado).body(respuesta);
    }
}