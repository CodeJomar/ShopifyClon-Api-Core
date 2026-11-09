package shopify.api.core.modulos.usuario.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DireccionResponse(
        UUID id,
        String nombreContacto,
        String telefono,
        String direccionLinea1,
        String direccionLinea2,
        String ciudad,
        String estadoProvincia,
        String codigoPostal,
        String pais,
        BigDecimal latitud,
        BigDecimal longitud,
        boolean esPrincipal,
        Instant fechaCreacion) {
}
