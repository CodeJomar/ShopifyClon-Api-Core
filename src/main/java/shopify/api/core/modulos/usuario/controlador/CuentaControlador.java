package shopify.api.core.modulos.usuario.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.modulos.usuario.dto.request.ActualizarPerfilRequest;
import shopify.api.core.modulos.usuario.dto.request.CambiarContrasenaRequest;
import shopify.api.core.modulos.usuario.dto.response.PerfilResponse;
import shopify.api.core.modulos.usuario.servicio.CuentaServicio;

@Tag(name = "Cuenta", description = "Perfil y contraseña del usuario en sesión")
@RestController
@RequestMapping("/cuenta")
@RequiredArgsConstructor
public class CuentaControlador {

    private final CuentaServicio cuentaServicio;

    @Operation(summary = "Ver mi perfil")
    @GetMapping("/perfil")
    public ResponseEntity<RespuestaApi<PerfilResponse>> perfil() {
        return RespuestaApi.ok(cuentaServicio.perfil(), "Perfil obtenido");
    }

    @Operation(summary = "Actualizar mi perfil")
    @PutMapping("/perfil")
    public ResponseEntity<RespuestaApi<PerfilResponse>> actualizarPerfil(@Valid @RequestBody ActualizarPerfilRequest request) {
        return RespuestaApi.ok(cuentaServicio.actualizarPerfil(request), "Perfil actualizado");
    }

    @Operation(summary = "Cambiar mi contraseña")
    @PutMapping("/contrasena")
    public ResponseEntity<RespuestaApi<Void>> cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest request) {
        cuentaServicio.cambiarContrasena(request);
        return RespuestaApi.ok(null, "Contraseña actualizada");
    }
}
