package shopify.api.core.comun.dto;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Formato único de respuesta (éxito y error), según {@code docs/negocio/modulos/transversal.md}.
 * Los nulos se omiten en el JSON.
 */
@Getter
@Builder
public class RespuestaApi<T> {

    public static final String CLAVE_CORRELACION_MDC = "correlacionId";

    private boolean exito;
    private int codigoEstado;
    private String codigo;
    private String mensaje;
    private T datos;
    private Map<String, Object> detalles;
    private Map<String, String> validaciones;
    private String ruta;
    private String idCorrelacion;
    private Instant marcaTiempo;

    public static String correlacionActual() {
        return MDC.get(CLAVE_CORRELACION_MDC);
    }

    public static <T> ResponseEntity<RespuestaApi<T>> ok(T datos, String mensaje) {
        return ResponseEntity.ok(exitosa(HttpStatus.OK, datos, mensaje));
    }

    public static <T> ResponseEntity<RespuestaApi<T>> creado(T datos, String mensaje) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exitosa(HttpStatus.CREATED, datos, mensaje));
    }

    public static <T> ResponseEntity<RespuestaApi<ConsultaPaginadaDto<T>>> paginado(Page<T> pagina, String mensaje) {
        return ResponseEntity.ok(exitosa(HttpStatus.OK, ConsultaPaginadaDto.desdePagina(pagina), mensaje));
    }

    public static ResponseEntity<RespuestaApi<Void>> error(
            HttpStatus estado, String codigo, String mensaje, String ruta,
            Map<String, String> validaciones, Map<String, Object> detalles) {
        return ResponseEntity.status(estado).body(cuerpoError(estado, codigo, mensaje, ruta, validaciones, detalles));
    }

    /** Cuerpo de error para quien escribe la respuesta a mano (filtros de seguridad). */
    public static RespuestaApi<Void> cuerpoError(
            HttpStatus estado, String codigo, String mensaje, String ruta,
            Map<String, String> validaciones, Map<String, Object> detalles) {
        return RespuestaApi.<Void>builder()
                .exito(false)
                .codigoEstado(estado.value())
                .codigo(codigo)
                .mensaje(mensaje)
                .detalles(detalles == null || detalles.isEmpty() ? null : detalles)
                .validaciones(validaciones == null || validaciones.isEmpty() ? null : validaciones)
                .ruta(ruta)
                .idCorrelacion(correlacionActual())
                .marcaTiempo(Instant.now())
                .build();
    }

    private static <T> RespuestaApi<T> exitosa(HttpStatus estado, T datos, String mensaje) {
        return RespuestaApi.<T>builder()
                .exito(true)
                .codigoEstado(estado.value())
                .mensaje(mensaje)
                .datos(datos)
                .idCorrelacion(correlacionActual())
                .marcaTiempo(Instant.now())
                .build();
    }
}
