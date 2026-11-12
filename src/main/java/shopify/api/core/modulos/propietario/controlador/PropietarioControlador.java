package shopify.api.core.modulos.propietario.controlador;

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
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse;
import shopify.api.core.modulos.propietario.servicio.ResumenPropietarioServicio;

/** Panel general del propietario: todas sus tiendas en una vista. Requiere sesión, no una tienda activa. */
@Tag(name = "Propietario", description = "Resumen de todas las tiendas de la persona")
@RestController
@RequestMapping("/propietario")
@RequiredArgsConstructor
public class PropietarioControlador {

    private final ResumenPropietarioServicio resumen;

    @Operation(summary = "Ventas, saldo, suscripción y alertas de cada tienda propia, con totales por moneda")
    @GetMapping("/resumen")
    public ResponseEntity<RespuestaApi<ResumenPropietarioResponse>> resumen(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return RespuestaApi.ok(resumen.resumen(desde, hasta), "Resumen del propietario");
    }
}
