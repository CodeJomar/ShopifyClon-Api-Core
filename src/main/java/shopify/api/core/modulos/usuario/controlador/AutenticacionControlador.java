package shopify.api.core.modulos.usuario.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.modulos.usuario.dto.request.ActivarCuentaRequest;
import shopify.api.core.modulos.usuario.dto.request.CorreoRequest;
import shopify.api.core.modulos.usuario.dto.request.LoginRequest;
import shopify.api.core.modulos.usuario.dto.request.RegistroRequest;
import shopify.api.core.modulos.usuario.dto.request.RestablecerClaveRequest;
import shopify.api.core.modulos.usuario.dto.response.LoginResponse;
import shopify.api.core.modulos.usuario.dto.response.RegistroResponse;
import shopify.api.core.modulos.usuario.servicio.AutenticacionServicio;
import shopify.api.core.modulos.usuario.servicio.RecuperacionClaveServicio;

@Tag(name = "Autenticación", description = "Registro, activación, inicio de sesión y recuperación de clave")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AutenticacionControlador {

    private final AutenticacionServicio autenticacionServicio;
    private final RecuperacionClaveServicio recuperacionClaveServicio;

    @Operation(summary = "Registrar una cuenta (queda sin activar y recibe un código por correo)")
    @PostMapping("/registro")
    @LimitarPeticiones(porMinuto = 10)
    public ResponseEntity<RespuestaApi<RegistroResponse>> registrar(@Valid @RequestBody RegistroRequest request) {
        return RespuestaApi.creado(autenticacionServicio.registrar(request),
                "Cuenta creada. Revisa tu correo para activarla");
    }

    @Operation(summary = "Activar la cuenta con el código de 6 dígitos")
    @PostMapping("/activar")
    @LimitarPeticiones(porMinuto = 10)
    public ResponseEntity<RespuestaApi<Void>> activar(@Valid @RequestBody ActivarCuentaRequest request) {
        autenticacionServicio.activar(request);
        return RespuestaApi.ok(null, "Tu cuenta quedó activada");
    }

    @Operation(summary = "Reenviar el código de activación")
    @PostMapping("/reenviar-activacion")
    @LimitarPeticiones(porMinuto = 10)
    public ResponseEntity<RespuestaApi<Void>> reenviarActivacion(@Valid @RequestBody CorreoRequest request) {
        autenticacionServicio.reenviarActivacion(request.correoElectronico());
        return RespuestaApi.ok(null, "Si la cuenta existe y no está activada, te enviamos un código nuevo");
    }

    @Operation(summary = "Iniciar sesión")
    @PostMapping("/login")
    @LimitarPeticiones(porMinuto = 10)
    public ResponseEntity<RespuestaApi<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return RespuestaApi.ok(autenticacionServicio.login(request), "Sesión iniciada");
    }

    @Operation(summary = "Solicitar un código para recuperar la clave")
    @PostMapping("/recuperar-clave/solicitar")
    @LimitarPeticiones(porMinuto = 10)
    public ResponseEntity<RespuestaApi<Void>> solicitarRecuperacion(@Valid @RequestBody CorreoRequest request) {
        recuperacionClaveServicio.solicitar(request.correoElectronico());
        return RespuestaApi.ok(null, "Si el correo está registrado, te enviamos un código");
    }

    @Operation(summary = "Restablecer la clave con el código recibido")
    @PostMapping("/recuperar-clave/confirmar")
    @LimitarPeticiones(porMinuto = 10)
    public ResponseEntity<RespuestaApi<Void>> confirmarRecuperacion(@Valid @RequestBody RestablecerClaveRequest request) {
        recuperacionClaveServicio.confirmar(request);
        return RespuestaApi.ok(null, "Tu contraseña fue restablecida");
    }
}
