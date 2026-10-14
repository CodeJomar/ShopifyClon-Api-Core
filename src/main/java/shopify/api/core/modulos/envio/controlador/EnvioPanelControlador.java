package shopify.api.core.modulos.envio.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.envio.dto.request.EnvioRequest;
import shopify.api.core.modulos.envio.dto.response.EnvioResponse;
import shopify.api.core.modulos.envio.servicio.EnvioServicio;

@Tag(name = "Panel: envíos", description = "Preparar, despachar y entregar órdenes (requiere X-Tienda-Id)")
@RestController
@RequiredArgsConstructor
public class EnvioPanelControlador {

    private final EnvioServicio envios;

    @Operation(summary = "Preparar la orden: crea el envío (ENV-003)")
    @PostMapping("/panel/ordenes/{ordenId}/envio")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<EnvioResponse>> crear(ContextoTienda contexto, @PathVariable UUID ordenId,
                                                             @Valid @RequestBody EnvioRequest request) {
        return RespuestaApi.creado(envios.crear(contexto.tiendaId(), ordenId, request), "Envío creado");
    }

    @Operation(summary = "Envío de la orden")
    @GetMapping("/panel/ordenes/{ordenId}/envio")
    @RequierePermiso(Permiso.ORDENES_VER)
    public ResponseEntity<RespuestaApi<EnvioResponse>> obtener(ContextoTienda contexto, @PathVariable UUID ordenId) {
        return RespuestaApi.ok(envios.obtenerDeOrden(contexto.tiendaId(), ordenId), "Envío");
    }

    @Operation(summary = "Editar transportista, guía, URL y fecha estimada (mientras no esté entregado)")
    @PutMapping("/panel/envios/{envioId}")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<EnvioResponse>> actualizar(ContextoTienda contexto, @PathVariable UUID envioId,
                                                                  @Valid @RequestBody EnvioRequest request) {
        return RespuestaApi.ok(envios.actualizar(contexto.tiendaId(), envioId, request), "Envío actualizado");
    }

    @Operation(summary = "Despachar el envío (CREADO → EN_TRANSITO)")
    @PostMapping("/panel/envios/{envioId}/despachar")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<EnvioResponse>> despachar(ContextoTienda contexto, @PathVariable UUID envioId) {
        return RespuestaApi.ok(envios.despachar(contexto.tiendaId(), envioId), "Envío despachado");
    }

    @Operation(summary = "Registrar la entrega (EN_TRANSITO → ENTREGADO)")
    @PostMapping("/panel/envios/{envioId}/entregar")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<EnvioResponse>> entregar(ContextoTienda contexto, @PathVariable UUID envioId) {
        return RespuestaApi.ok(envios.entregar(contexto.tiendaId(), envioId), "Envío entregado");
    }

    @Operation(summary = "Registrar la devolución (EN_TRANSITO → DEVUELTO)")
    @PostMapping("/panel/envios/{envioId}/devolver")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<EnvioResponse>> devolver(ContextoTienda contexto, @PathVariable UUID envioId) {
        return RespuestaApi.ok(envios.devolver(contexto.tiendaId(), envioId), "Envío devuelto");
    }
}
