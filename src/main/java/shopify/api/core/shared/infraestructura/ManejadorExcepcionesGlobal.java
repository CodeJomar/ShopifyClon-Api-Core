package shopify.api.core.shared.infraestructura;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import shopify.api.core.shared.dto.RespuestaApi;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;
import shopify.api.core.shared.exception.ReglaNegocioException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ManejadorExcepcionesGlobal {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarNoEncontrado(
            RecursoNoEncontradoException ex, HttpServletRequest request) {
        return RespuestaApi.error(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                "Recurso no encontrado",
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarReglaNegocio(
            ReglaNegocioException ex, HttpServletRequest request) {
        return RespuestaApi.error(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                "Conflicto con regla de negocio",
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarValidaciones(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> validaciones = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validaciones.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return RespuestaApi.error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Uno o más campos no cumplen con el formato requerido",
                "Fallo de validación de datos",
                request.getRequestURI(),
                validaciones
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaApi<Void>> manejarGeneral(
            Exception ex, HttpServletRequest request) {
        return RespuestaApi.error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado al procesar la solicitud",
                "Error interno del servidor",
                request.getRequestURI(),
                null
        );
    }
}