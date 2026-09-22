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
import shopify.api.core.modules.usuario.application.casouso.RegistrarUsuarioCasoUso;
import shopify.api.core.modules.usuario.application.dto.request.RegistroUsuarioRequest;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Usuarios y Seguridad", description = "Endpoints de autenticación y gestión de usuarios")
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class RegistrarUsuarioControlador {

    private final RegistrarUsuarioCasoUso casoUso;

    @Operation(summary = "Registrar nuevo usuario (Cliente o Admin de Tienda)")
    @PostMapping("/registro")
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> registrar(@Valid @RequestBody RegistroUsuarioRequest request) {
        EstadoOperacionDto resultado = casoUso.ejecutar(request);
        return RespuestaApi.creado(resultado, resultado.getMensaje());
    }
}