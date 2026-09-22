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
import shopify.api.core.modules.usuario.application.casouso.AutenticarUsuarioCasoUso;
import shopify.api.core.modules.usuario.application.dto.request.LoginUsuarioRequest;
import shopify.api.core.modules.usuario.application.dto.response.AuthResponse;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Usuarios y Seguridad")
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class AutenticarUsuarioControlador {

    private final AutenticarUsuarioCasoUso casoUso;

    @Operation(summary = "Iniciar sesión y obtener token JWT Bearer")
    @PostMapping("/login")
    public ResponseEntity<RespuestaApi<AuthResponse>> login(@Valid @RequestBody LoginUsuarioRequest request) {
        AuthResponse respuesta = casoUso.ejecutar(request);
        return RespuestaApi.ok(respuesta, "Autenticación exitosa");
    }
}