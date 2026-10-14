package shopify.api.core.modulos.carrito.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.modulos.carrito.dto.request.AgregarItemRequest;
import shopify.api.core.modulos.carrito.dto.request.CambiarCantidadRequest;
import shopify.api.core.modulos.carrito.dto.request.CuponCarritoRequest;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse;
import shopify.api.core.modulos.carrito.servicio.CarritoServicio;

/** Carrito público: el token del carrito viaja en {@code X-Carrito-Token} (CAR-001). */
@Tag(name = "Vitrina: carrito", description = "Carrito del comprador por subdominio")
@RestController
@RequestMapping("/vitrina/{subdominio}/carrito")
@RequiredArgsConstructor
public class CarritoVitrinaControlador {

    static final String ENCABEZADO_TOKEN = "X-Carrito-Token";

    private final CarritoServicio carritoServicio;

    @Operation(summary = "Crear un carrito (devuelve su token)")
    @PostMapping
    @LimitarPeticiones(porMinuto = 30)
    public ResponseEntity<RespuestaApi<CarritoResponse>> crear(@PathVariable String subdominio) {
        return RespuestaApi.creado(carritoServicio.crear(subdominio), "Carrito creado");
    }

    @Operation(summary = "Ver el carrito recalculado con precios actuales")
    @GetMapping
    public ResponseEntity<RespuestaApi<CarritoResponse>> ver(
            @PathVariable String subdominio, @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token) {
        return RespuestaApi.ok(carritoServicio.obtener(subdominio, token), "Carrito");
    }

    @Operation(summary = "Agregar una variante (suma cantidades)")
    @PostMapping("/items")
    public ResponseEntity<RespuestaApi<CarritoResponse>> agregar(
            @PathVariable String subdominio,
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token,
            @Valid @RequestBody AgregarItemRequest request) {
        return RespuestaApi.ok(carritoServicio.agregar(subdominio, token, request.varianteId(), request.cantidad()),
                "Producto agregado");
    }

    @Operation(summary = "Fijar la cantidad de una variante (0 la quita)")
    @PutMapping("/items/{varianteId}")
    public ResponseEntity<RespuestaApi<CarritoResponse>> fijarCantidad(
            @PathVariable String subdominio,
            @PathVariable UUID varianteId,
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token,
            @Valid @RequestBody CambiarCantidadRequest request) {
        return RespuestaApi.ok(carritoServicio.fijarCantidad(subdominio, token, varianteId, request.cantidad()),
                "Carrito actualizado");
    }

    @Operation(summary = "Quitar una variante del carrito")
    @DeleteMapping("/items/{varianteId}")
    public ResponseEntity<RespuestaApi<CarritoResponse>> quitar(
            @PathVariable String subdominio,
            @PathVariable UUID varianteId,
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token) {
        return RespuestaApi.ok(carritoServicio.quitar(subdominio, token, varianteId), "Producto quitado");
    }

    @Operation(summary = "Aplicar un cupón (reemplaza el anterior)")
    @PutMapping("/cupon")
    public ResponseEntity<RespuestaApi<CarritoResponse>> aplicarCupon(
            @PathVariable String subdominio,
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token,
            @Valid @RequestBody CuponCarritoRequest request) {
        return RespuestaApi.ok(carritoServicio.aplicarCupon(subdominio, token, request.codigo()), "Cupón aplicado");
    }

    @Operation(summary = "Quitar el cupón")
    @DeleteMapping("/cupon")
    public ResponseEntity<RespuestaApi<CarritoResponse>> quitarCupon(
            @PathVariable String subdominio, @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token) {
        return RespuestaApi.ok(carritoServicio.quitarCupon(subdominio, token), "Cupón quitado");
    }

    @Operation(summary = "Vincular el carrito a la sesión (requiere sesión; fusiona si ya hay uno)")
    @PostMapping("/vincular")
    public ResponseEntity<RespuestaApi<CarritoResponse>> vincular(
            @PathVariable String subdominio, @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token) {
        return RespuestaApi.ok(carritoServicio.vincular(subdominio, token), "Carrito vinculado");
    }
}
