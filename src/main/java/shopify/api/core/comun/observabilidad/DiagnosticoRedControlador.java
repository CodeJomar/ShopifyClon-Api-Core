package shopify.api.core.comun.observabilidad;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.IpClienteResolver;
import shopify.api.core.comun.seguridad.SoloAdministradorPlataforma;

/**
 * A-007: muestra al administrador de plataforma cómo llega su propia petición a la API (IP de la conexión y cabeceras
 * de IP que añade el proxy). Sirve para elegir {@code PROXY_CABECERA_IP} en cada plataforma de despliegue sin adivinar.
 * Solo devuelve datos de la petición de quien consulta.
 */
@Tag(name = "Plataforma: diagnóstico", description = "Cómo ve la API la red de quien consulta (solo administrador de plataforma)")
@RestController
@RequestMapping("/plataforma/diagnostico")
@SoloAdministradorPlataforma
@RequiredArgsConstructor
public class DiagnosticoRedControlador {

    private static final List<String> CABECERAS = List.of(
            "X-Real-IP", "X-Forwarded-For", "X-Forwarded-Proto", "X-Forwarded-Host", "Forwarded",
            "X-Envoy-External-Address", "True-Client-IP", "Fastly-Client-IP", "CF-Connecting-IP",
            "X-Client-IP", "X-Railway-Edge", "CDN-Loop", "Via");

    private final IpClienteResolver ipClienteResolver;

    @Operation(summary = "IP de la conexión, cabeceras de IP recibidas e IP que usa el límite de peticiones")
    @GetMapping("/red")
    public ResponseEntity<RespuestaApi<Map<String, Object>>> red(HttpServletRequest request) {
        Map<String, String> cabeceras = new LinkedHashMap<>();
        for (String nombre : CABECERAS) {
            String valor = request.getHeader(nombre);
            if (valor != null) {
                cabeceras.put(nombre, valor);
            }
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ip_conexion", request.getRemoteAddr());
        datos.put("ip_para_el_limite", ipClienteResolver.ipDe(request));
        datos.put("cabeceras", cabeceras);
        return RespuestaApi.ok(datos, "Diagnóstico de red");
    }
}
