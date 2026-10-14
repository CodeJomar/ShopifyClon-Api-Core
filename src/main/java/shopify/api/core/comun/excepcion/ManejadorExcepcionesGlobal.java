package shopify.api.core.comun.excepcion;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import shopify.api.core.comun.dto.RespuestaApi;

@Slf4j
@RestControllerAdvice
public class ManejadorExcepcionesGlobal {

    @ExceptionHandler(ExcepcionNegocio.class)
    public ResponseEntity<RespuestaApi<Void>> manejarNegocio(ExcepcionNegocio ex, HttpServletRequest request) {
        return RespuestaApi.error(ex.getEstado(), ex.getCodigo(), ex.getMessage(),
                request.getRequestURI(), null, ex.getDetalles());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarValidaciones(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> validaciones = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            validaciones.putIfAbsent(aSnakeCase(error.getField()), error.getDefaultMessage());
        }
        return RespuestaApi.error(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-001", "Revisa los datos marcados",
                request.getRequestURI(), validaciones, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarCuerpoIlegible(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return RespuestaApi.error(HttpStatus.BAD_REQUEST, "VAL-002", "No pudimos leer los datos enviados",
                request.getRequestURI(), null, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarTipoParametro(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return parametroInvalido(ex.getName(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarParametroFaltante(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return parametroInvalido(ex.getParameterName(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<RespuestaApi<Void>> manejarRutaInexistente(Exception ex, HttpServletRequest request) {
        return RespuestaApi.error(HttpStatus.NOT_FOUND, "GEN-404", "La ruta solicitada no existe",
                request.getRequestURI(), null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarMetodoNoSoportado(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return RespuestaApi.error(HttpStatus.METHOD_NOT_ALLOWED, "GEN-405", "Método no permitido en esta ruta",
                request.getRequestURI(), null, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarIntegridad(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violación de integridad en {}: restricción {}", request.getRequestURI(), nombreRestriccion(ex));
        return RespuestaApi.error(HttpStatus.CONFLICT, "GEN-409",
                "La operación entra en conflicto con datos existentes", request.getRequestURI(), null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarAccesoDenegado(
            AccessDeniedException ex, HttpServletRequest request) {
        return RespuestaApi.error(HttpStatus.FORBIDDEN, "SEG-002", "Tu rol no tiene permiso para esta acción",
                request.getRequestURI(), null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaApi<Void>> manejarGeneral(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        String correlacion = RespuestaApi.correlacionActual();
        return RespuestaApi.error(HttpStatus.INTERNAL_SERVER_ERROR, "GEN-500",
                "Ocurrió un error inesperado. Código de seguimiento: " + (correlacion == null ? "no disponible" : correlacion),
                request.getRequestURI(), null, null);
    }

    private ResponseEntity<RespuestaApi<Void>> parametroInvalido(String nombre, HttpServletRequest request) {
        return RespuestaApi.error(HttpStatus.BAD_REQUEST, "VAL-003",
                "El parámetro «" + nombre + "» no tiene un formato válido", request.getRequestURI(), null, null);
    }

    private static String nombreRestriccion(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
        }
        return "desconocida";
    }

    private static String aSnakeCase(String campo) {
        return campo.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
