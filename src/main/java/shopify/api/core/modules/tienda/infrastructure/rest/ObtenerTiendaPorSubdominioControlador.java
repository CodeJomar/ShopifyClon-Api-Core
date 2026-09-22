package shopify.api.core.modules.tienda.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.tienda.application.casouso.ObtenerTiendaPorSubdominioCasoUso;
import shopify.api.core.modules.tienda.application.dto.response.TiendaResponse;
import shopify.api.core.shared.dto.ConsultaIndividualDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Tiendas")
@RestController
@RequestMapping("/tiendas")
@RequiredArgsConstructor
public class ObtenerTiendaPorSubdominioControlador {

    private final ObtenerTiendaPorSubdominioCasoUso casoUso;

    @Operation(summary = "Obtener configuración de la tienda por su subdominio (Público)")
    @GetMapping("/{subdominio}")
    public ResponseEntity<RespuestaApi<ConsultaIndividualDto<TiendaResponse>>> obtenerPorSubdominio(
            @PathVariable("subdominio") String subdominio) {
        TiendaResponse respuesta = casoUso.ejecutar(subdominio);
        return RespuestaApi.ok(ConsultaIndividualDto.de(respuesta), "Tienda encontrada");
    }
}