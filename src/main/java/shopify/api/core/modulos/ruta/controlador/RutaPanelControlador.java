package shopify.api.core.modulos.ruta.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.ConsultaPaginadaDto;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.ruta.dto.request.RutaRequest;
import shopify.api.core.modulos.ruta.dto.response.EnvioDisponibleResponse;
import shopify.api.core.modulos.ruta.dto.response.RutaDetalleResponse;
import shopify.api.core.modulos.ruta.dto.response.RutaListaResponse;
import shopify.api.core.modulos.ruta.modelo.EstadoRuta;
import shopify.api.core.modulos.ruta.servicio.RutaServicio;

@Tag(name = "Panel: rutas de reparto", description = "Hojas de ruta y guía de remisión (requiere X-Tienda-Id)")
@RestController
@RequiredArgsConstructor
public class RutaPanelControlador {

    private final RutaServicio rutaServicio;

    @Operation(summary = "Listar las rutas (paginado; filtros estado y fecha)")
    @GetMapping("/panel/rutas")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<RutaListaResponse>>> listar(
            ContextoTienda contexto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(required = false) EstadoRuta estado,
            @Parameter(description = "AAAA-MM-DD")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return RespuestaApi.paginado(rutaServicio.listar(contexto, pagina, estado, fecha), "Rutas obtenidas");
    }

    @Operation(summary = "Crear una ruta en borrador con sus envíos en orden (RUT-001, RUT-002)")
    @PostMapping("/panel/rutas")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<RutaDetalleResponse>> crear(
            ContextoTienda contexto, @Valid @RequestBody RutaRequest request) {
        return RespuestaApi.creado(rutaServicio.crear(contexto, request), "Ruta creada");
    }

    @Operation(summary = "Detalle de la ruta con todo lo necesario para la guía de remisión")
    @GetMapping("/panel/rutas/{id}")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<RutaDetalleResponse>> obtener(ContextoTienda contexto, @PathVariable UUID id) {
        return RespuestaApi.ok(rutaServicio.obtener(contexto, id), "Ruta");
    }

    @Operation(summary = "Editar datos y paradas (solo en borrador, RUT-005)")
    @PutMapping("/panel/rutas/{id}")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<RutaDetalleResponse>> actualizar(
            ContextoTienda contexto, @PathVariable UUID id, @Valid @RequestBody RutaRequest request) {
        return RespuestaApi.ok(rutaServicio.actualizar(contexto, id, request), "Ruta actualizada");
    }

    @Operation(summary = "Iniciar la ruta: BORRADOR → EN_CURSO y despacha los envíos creados (RUT-003)")
    @PostMapping("/panel/rutas/{id}/iniciar")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<RutaDetalleResponse>> iniciar(ContextoTienda contexto, @PathVariable UUID id) {
        return RespuestaApi.ok(rutaServicio.iniciar(contexto, id), "Ruta iniciada");
    }

    @Operation(summary = "Finalizar la ruta: EN_CURSO → FINALIZADA (RUT-004)")
    @PostMapping("/panel/rutas/{id}/finalizar")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<RutaDetalleResponse>> finalizar(ContextoTienda contexto, @PathVariable UUID id) {
        return RespuestaApi.ok(rutaServicio.finalizar(contexto, id), "Ruta finalizada");
    }

    @Operation(summary = "Envíos creados o en tránsito que aún no están en una ruta activa")
    @GetMapping("/panel/envios/disponibles-para-ruta")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<List<EnvioDisponibleResponse>>> disponibles(ContextoTienda contexto) {
        return RespuestaApi.ok(rutaServicio.disponibles(contexto), "Envíos disponibles");
    }
}
