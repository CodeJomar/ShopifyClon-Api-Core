package shopify.api.core.modulos.balance.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.balance.dto.response.MetricasResponse;
import shopify.api.core.modulos.balance.servicio.MetricasServicio;

/** Métricas del panel del comerciante (MET-001), con permiso METRICAS_VER. */
@Tag(name = "Panel: métricas", description = "Cifras de ventas por día (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/metricas")
@RequiredArgsConstructor
public class MetricasControlador {

    private final MetricasServicio metricas;

    @Operation(summary = "Métricas del rango en la zona horaria de la tienda (máximo 366 días)")
    @GetMapping
    @RequierePermiso(Permiso.METRICAS_VER)
    public ResponseEntity<RespuestaApi<MetricasResponse>> panel(
            ContextoTienda contexto,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return RespuestaApi.ok(metricas.panel(contexto.tiendaId(), desde, hasta), "Métricas");
    }
}
