package shopify.api.core.modulos.carrito.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import shopify.api.core.modulos.carrito.dto.request.CotizarCarritoRequest;
import shopify.api.core.modulos.carrito.dto.request.CuponGlobalRequest;
import shopify.api.core.modulos.carrito.dto.response.CarritoGlobalResponse;
import shopify.api.core.modulos.carrito.dto.response.CarritoGlobalResponse.TarifasDeTienda;
import shopify.api.core.modulos.carrito.servicio.CarritoGlobalServicio;

/** Carrito global (R-001, B4): líneas de varias tiendas con el mismo {@code X-Carrito-Token}. */
@Tag(name = "Carrito global", description = "Un carrito con productos de varias tiendas (una sola moneda)")
@RestController
@RequestMapping("/carrito")
@RequiredArgsConstructor
public class CarritoGlobalControlador {

    static final String ENCABEZADO_TOKEN = "X-Carrito-Token";

    private final CarritoGlobalServicio servicio;

    @Operation(summary = "Crear un carrito vacío (devuelve su token)")
    @PostMapping
    @LimitarPeticiones(porMinuto = 30)
    public ResponseEntity<RespuestaApi<CarritoGlobalResponse>> crear() {
        return RespuestaApi.creado(servicio.crear(), "Carrito creado");
    }

    @Operation(summary = "Ver el carrito agrupado por tienda, con total general")
    @GetMapping
    @LimitarPeticiones(porMinuto = 120)
    public ResponseEntity<RespuestaApi<CarritoGlobalResponse>> ver(
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token) {
        return RespuestaApi.ok(servicio.ver(token), "Carrito");
    }

    @Operation(summary = "Agregar una variante de cualquier tienda (CAR-009 si la moneda no coincide)")
    @PostMapping("/items")
    @LimitarPeticiones(porMinuto = 120)
    public ResponseEntity<RespuestaApi<CarritoGlobalResponse>> agregar(
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token,
            @Valid @RequestBody AgregarItemRequest request) {
        return RespuestaApi.ok(servicio.agregar(token, request.varianteId(), request.cantidad()), "Producto agregado");
    }

    @Operation(summary = "Fijar la cantidad de una variante (0 la quita)")
    @PutMapping("/items/{varianteId}")
    @LimitarPeticiones(porMinuto = 120)
    public ResponseEntity<RespuestaApi<CarritoGlobalResponse>> fijarCantidad(
            @PathVariable UUID varianteId,
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token,
            @Valid @RequestBody CambiarCantidadRequest request) {
        return RespuestaApi.ok(servicio.fijarCantidad(token, varianteId, request.cantidad()), "Carrito actualizado");
    }

    @Operation(summary = "Quitar una variante del carrito")
    @DeleteMapping("/items/{varianteId}")
    @LimitarPeticiones(porMinuto = 120)
    public ResponseEntity<RespuestaApi<CarritoGlobalResponse>> quitar(
            @PathVariable UUID varianteId, @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token) {
        return RespuestaApi.ok(servicio.fijarCantidad(token, varianteId, 0), "Producto quitado");
    }

    @Operation(summary = "Tarifas de envío activas de cada tienda del carrito")
    @GetMapping("/tarifas")
    @LimitarPeticiones(porMinuto = 120)
    public ResponseEntity<RespuestaApi<List<TarifasDeTienda>>> tarifas(
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token) {
        return RespuestaApi.ok(servicio.tarifas(token), "Tarifas de envío");
    }

    @Operation(summary = "Cotizar con la tarifa elegida por tienda (mismo total que calculará el checkout)")
    @PostMapping("/cotizar")
    @LimitarPeticiones(porMinuto = 120)
    public ResponseEntity<RespuestaApi<CarritoGlobalResponse>> cotizar(
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token,
            @Valid @RequestBody CotizarCarritoRequest request) {
        return RespuestaApi.ok(servicio.cotizar(token, request.envios()), "Cotización");
    }

    @Operation(summary = "Aplicar un cupón a las líneas de una tienda (reemplaza el anterior de esa tienda)")
    @PutMapping("/cupones")
    @LimitarPeticiones(porMinuto = 60)
    public ResponseEntity<RespuestaApi<CarritoGlobalResponse>> aplicarCupon(
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token,
            @Valid @RequestBody CuponGlobalRequest request) {
        return RespuestaApi.ok(servicio.aplicarCupon(token, request.tiendaId(), request.codigo()), "Cupón aplicado");
    }

    @Operation(summary = "Quitar el cupón de una tienda")
    @DeleteMapping("/cupones/{tiendaId}")
    @LimitarPeticiones(porMinuto = 60)
    public ResponseEntity<RespuestaApi<CarritoGlobalResponse>> quitarCupon(
            @PathVariable UUID tiendaId, @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token) {
        return RespuestaApi.ok(servicio.quitarCupon(token, tiendaId), "Cupón quitado");
    }

    @Operation(summary = "Vincular el carrito a la sesión (requiere sesión; fusiona con el que ya tenga la persona)")
    @PostMapping("/vincular")
    @LimitarPeticiones(porMinuto = 30)
    public ResponseEntity<RespuestaApi<CarritoGlobalResponse>> vincular(
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token) {
        return RespuestaApi.ok(servicio.vincular(token), "Carrito vinculado");
    }
}
