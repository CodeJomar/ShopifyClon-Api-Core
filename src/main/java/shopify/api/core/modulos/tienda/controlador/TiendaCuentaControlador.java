package shopify.api.core.modulos.tienda.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.modulos.tienda.dto.request.CrearTiendaRequest;
import shopify.api.core.modulos.tienda.dto.response.MiTiendaResponse;
import shopify.api.core.modulos.tienda.dto.response.TiendaCreadaResponse;
import shopify.api.core.modulos.tienda.servicio.AltaTiendaServicio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

@Tag(name = "Tiendas del usuario", description = "Crear una tienda y ver las tiendas en las que participo")
@RestController
@RequestMapping("/cuenta/tiendas")
@RequiredArgsConstructor
public class TiendaCuentaControlador {

    private final AltaTiendaServicio altaTiendaServicio;
    private final TiendaServicio tiendaServicio;

    @Operation(summary = "Crear una tienda (queda yo como propietario)")
    @PostMapping
    @LimitarPeticiones(porMinuto = 10)
    public ResponseEntity<RespuestaApi<TiendaCreadaResponse>> crear(@Valid @RequestBody CrearTiendaRequest request) {
        return RespuestaApi.creado(altaTiendaServicio.crear(request), "Tienda creada");
    }

    @Operation(summary = "Ver las tiendas en las que participo")
    @GetMapping
    public ResponseEntity<RespuestaApi<List<MiTiendaResponse>>> misTiendas() {
        return RespuestaApi.ok(tiendaServicio.misTiendas(), "Tiendas obtenidas");
    }
}
