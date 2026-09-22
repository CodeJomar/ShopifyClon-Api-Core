package shopify.api.core.modules.usuario.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.usuario.application.casouso.ObtenerPerfilUsuarioCasoUso;
import shopify.api.core.modules.usuario.application.dto.response.UsuarioResponse;
import shopify.api.core.shared.dto.ConsultaIndividualDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Usuarios y Seguridad")
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class ObtenerPerfilUsuarioControlador {

    private final ObtenerPerfilUsuarioCasoUso casoUso;

    @Operation(summary = "Obtener el perfil del usuario autenticado actual", security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/perfil")
    public ResponseEntity<RespuestaApi<ConsultaIndividualDto<UsuarioResponse>>> obtenerPerfil() {
        UsuarioResponse respuesta = casoUso.ejecutar();
        return RespuestaApi.ok(ConsultaIndividualDto.de(respuesta), "Perfil de usuario recuperado exitosamente");
    }
}