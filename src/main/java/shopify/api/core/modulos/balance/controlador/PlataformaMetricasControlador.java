package shopify.api.core.modulos.balance.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.ConsultaPaginadaDto;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.SoloAdministradorPlataforma;
import shopify.api.core.modulos.balance.dto.response.ComisionTiendaResponse;
import shopify.api.core.modulos.balance.servicio.MetricasServicio;

/** Comisiones de la plataforma por tienda. Solo administrador de plataforma; para el resto, 404 SEG-006. */
@Tag(name = "Plataforma: métricas", description = "Comisiones por tienda")
@RestController
@RequestMapping("/plataforma/metricas")
@SoloAdministradorPlataforma
@RequiredArgsConstructor
public class PlataformaMetricasControlador {

    private final MetricasServicio metricas;

    @Operation(summary = "Comisiones por tienda, de mayor a menor (paginado de 20)")
    @GetMapping("/comisiones")
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<ComisionTiendaResponse>>> comisiones(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        Page<ComisionTiendaResponse> resultado = metricas.comisionesPorTienda(pagina);
        return RespuestaApi.paginado(resultado, "Comisiones por tienda");
    }
}
