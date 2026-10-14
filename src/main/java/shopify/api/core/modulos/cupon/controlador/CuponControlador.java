package shopify.api.core.modulos.cupon.controlador;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.ConsultaPaginadaDto;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.cupon.dto.request.CuponRequest;
import shopify.api.core.modulos.cupon.dto.response.CuponResponse;
import shopify.api.core.modulos.cupon.servicio.CuponServicio;

@Tag(name = "Panel: cupones", description = "Cupones de descuento de la tienda (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/cupones")
@RequiredArgsConstructor
public class CuponControlador {

    private final CuponServicio cuponServicio;

    @Operation(summary = "Listar los cupones (paginado, con búsqueda y filtro de activos)")
    @GetMapping
    @RequierePermiso(Permiso.CUPONES_GESTIONAR)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<CuponResponse>>> listar(
            ContextoTienda contexto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean activo) {
        return RespuestaApi.paginado(cuponServicio.listar(contexto, pagina, texto, activo), "Cupones obtenidos");
    }

    @Operation(summary = "Ver un cupón")
    @GetMapping("/{id}")
    @RequierePermiso(Permiso.CUPONES_GESTIONAR)
    public ResponseEntity<RespuestaApi<CuponResponse>> obtener(ContextoTienda contexto, @PathVariable UUID id) {
        return RespuestaApi.ok(cuponServicio.obtener(contexto, id), "Cupón obtenido");
    }

    @Operation(summary = "Crear un cupón")
    @PostMapping
    @RequierePermiso(Permiso.CUPONES_GESTIONAR)
    public ResponseEntity<RespuestaApi<CuponResponse>> crear(
            ContextoTienda contexto, @Valid @RequestBody CuponRequest request) {
        return RespuestaApi.creado(cuponServicio.crear(contexto, request), "Cupón creado");
    }

    @Operation(summary = "Editar un cupón (CUP-008: con usos no cambia tipo ni valor)")
    @PutMapping("/{id}")
    @RequierePermiso(Permiso.CUPONES_GESTIONAR)
    public ResponseEntity<RespuestaApi<CuponResponse>> actualizar(
            ContextoTienda contexto, @PathVariable UUID id, @Valid @RequestBody CuponRequest request) {
        return RespuestaApi.ok(cuponServicio.actualizar(contexto, id, request), "Cupón actualizado");
    }

    @Operation(summary = "Eliminar un cupón (lógico)")
    @DeleteMapping("/{id}")
    @RequierePermiso(Permiso.CUPONES_GESTIONAR)
    public ResponseEntity<RespuestaApi<Void>> eliminar(ContextoTienda contexto, @PathVariable UUID id) {
        cuponServicio.eliminar(contexto, id);
        return RespuestaApi.ok(null, "Cupón eliminado");
    }
}
