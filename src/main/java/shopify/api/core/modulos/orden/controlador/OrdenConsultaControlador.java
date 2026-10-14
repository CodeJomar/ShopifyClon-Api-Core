package shopify.api.core.modulos.orden.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.ConsultaPaginadaDto;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.orden.dto.response.OrdenCuentaResponse;
import shopify.api.core.modulos.orden.dto.response.OrdenDetallePanelResponse;
import shopify.api.core.modulos.orden.dto.response.OrdenListaResponse;
import shopify.api.core.modulos.orden.dto.response.OrdenPublicaResponse;
import shopify.api.core.modulos.orden.servicio.OrdenConsultaServicio;

/** Lectura de órdenes: panel, historial del comprador y estado público por token. Solo consulta. */
@Tag(name = "Órdenes: consulta", description = "Listados y detalle de órdenes")
@RestController
@RequiredArgsConstructor
public class OrdenConsultaControlador {

    private final OrdenConsultaServicio consultas;

    @Operation(summary = "Listado de órdenes de la tienda con filtros (requiere X-Tienda-Id)")
    @GetMapping("/panel/ordenes")
    @RequierePermiso(Permiso.ORDENES_VER)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<OrdenListaResponse>>> listarPanel(
            ContextoTienda contexto,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "estado_pago", required = false) String estadoPago,
            @RequestParam(name = "estado_preparacion", required = false) String estadoPreparacion,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "texto", required = false) String texto) {
        return RespuestaApi.paginado(consultas.listarPanel(contexto.tiendaId(), pagina, estado, estadoPago,
                estadoPreparacion, desde, hasta, texto), "Órdenes");
    }

    @Operation(summary = "Detalle completo de una orden para el panel (incluye comisión)")
    @GetMapping("/panel/ordenes/{ordenId}")
    @RequierePermiso(Permiso.ORDENES_VER)
    public ResponseEntity<RespuestaApi<OrdenDetallePanelResponse>> detallePanel(ContextoTienda contexto,
                                                                               @PathVariable UUID ordenId) {
        return RespuestaApi.ok(consultas.detallePanel(contexto.tiendaId(), ordenId), "Orden");
    }

    @Operation(summary = "Historial de órdenes del comprador registrado")
    @GetMapping("/cuenta/ordenes")
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<OrdenCuentaResponse>>> historialDelUsuario(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tienda_id", required = false) UUID tiendaId) {
        return RespuestaApi.paginado(consultas.historialDelUsuario(SesionActual.usuarioId(), pagina, tiendaId), "Mis órdenes");
    }

    @Operation(summary = "Detalle de una orden del comprador registrado")
    @GetMapping("/cuenta/ordenes/{ordenId}")
    public ResponseEntity<RespuestaApi<OrdenPublicaResponse>> detalleDelUsuario(@PathVariable UUID ordenId) {
        return RespuestaApi.ok(consultas.detalleDelUsuario(SesionActual.usuarioId(), ordenId), "Orden");
    }

    @Operation(summary = "Estado de la orden para el comprador invitado, por su token de acceso")
    @GetMapping("/vitrina/{subdominio}/ordenes/{tokenAcceso}")
    public ResponseEntity<RespuestaApi<OrdenPublicaResponse>> porToken(@PathVariable String subdominio,
                                                                      @PathVariable String tokenAcceso) {
        return RespuestaApi.ok(consultas.porToken(subdominio, tokenAcceso), "Orden");
    }
}
