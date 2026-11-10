package shopify.api.core.comun.observabilidad;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;

/**
 * Comprobación de vida para Railway, Docker y quien monitoree la API. No toca la base de datos ni exige sesión: solo
 * dice que el proceso atiende peticiones.
 */
@Tag(name = "Salud", description = "Comprobación de que la API está viva")
@RestController
@RequestMapping("/health")
public class SaludControlador {

    @Operation(summary = "La API está viva (sin sesión y sin consultar la base de datos)")
    @GetMapping
    public ResponseEntity<RespuestaApi<Map<String, String>>> salud() {
        return RespuestaApi.ok(Map.of("estado", "UP"), "La API está viva");
    }
}
