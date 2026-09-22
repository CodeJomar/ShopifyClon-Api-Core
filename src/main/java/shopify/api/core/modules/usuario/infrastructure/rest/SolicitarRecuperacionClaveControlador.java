package shopify.api.core.modules.usuario.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.usuario.application.casouso.SolicitarRecuperacionClaveCasoUso;
import shopify.api.core.modules.usuario.application.dto.request.SolicitarRecuperacionRequest;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Usuarios y Seguridad")
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class SolicitarRecuperacionClaveControlador {

    private final SolicitarRecuperacionClaveCasoUso casoUso;

    @Operation(summary = "Solicitar código de 6 dígitos para recuperación de contraseña")
    @PostMapping("/recuperar-clave/solicitar")
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> solicitar(@Valid @RequestBody SolicitarRecuperacionRequest request) {
        EstadoOperacionDto resultado = casoUso.ejecutar(request);
        return RespuestaApi.ok(resultado, resultado.getMensaje());
    }
}