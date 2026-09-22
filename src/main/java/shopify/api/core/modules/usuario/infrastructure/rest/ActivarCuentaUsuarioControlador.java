package shopify.api.core.modules.usuario.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.usuario.application.casouso.ActivarCuentaUsuarioCasoUso;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Usuarios y Seguridad")
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class ActivarCuentaUsuarioControlador {

    private final ActivarCuentaUsuarioCasoUso casoUso;

    @Operation(summary = "Activar cuenta mediante token recibido por correo")
    @GetMapping("/activar-cuenta")
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> activar(@RequestParam("token") String token) {
        EstadoOperacionDto resultado = casoUso.ejecutar(token);
        return RespuestaApi.ok(resultado, resultado.getMensaje());
    }
}