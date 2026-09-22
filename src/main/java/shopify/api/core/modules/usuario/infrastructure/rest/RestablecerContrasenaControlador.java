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
import shopify.api.core.modules.usuario.application.casouso.RestablecerContrasenaCasoUso;
import shopify.api.core.modules.usuario.application.dto.request.RestablecerContrasenaRequest;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Usuarios y Seguridad")
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class RestablecerContrasenaControlador {

    private final RestablecerContrasenaCasoUso casoUso;

    @Operation(summary = "Validar código y cambiar la contraseña por la nueva")
    @PostMapping("/recuperar-clave/confirmar")
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> confirmar(@Valid @RequestBody RestablecerContrasenaRequest request) {
        EstadoOperacionDto resultado = casoUso.ejecutar(request);
        return RespuestaApi.ok(resultado, resultado.getMensaje());
    }
}