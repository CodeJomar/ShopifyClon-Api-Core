package shopify.api.core.modulos.respaldo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.comun.seguridad.PermisoPlataforma;
import shopify.api.core.comun.seguridad.RequierePermisoPlataforma;
import shopify.api.core.modulos.respaldo.servicio.RespaldoProgramador;
import shopify.api.core.modulos.respaldo.servicio.RespaldoServicio;
import shopify.api.core.modulos.respaldo.servicio.RespaldoServicio.Archivo;
import shopify.api.core.modulos.respaldo.servicio.RespaldoServicio.Resultado;

/**
 * Estado y ejecución manual de los respaldos de la base (R-023). Solo administradores con el permiso de seguridad. Los
 * archivos no se descargan por la API (contienen todos los datos): se recogen del volumen del servidor.
 */
@Tag(name = "Plataforma: respaldos", description = "Respaldo programado de la base de datos")
@RestController
@RequestMapping("/plataforma/respaldos")
@RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_SEGURIDAD)
@RequiredArgsConstructor
public class RespaldoControlador {

    private final RespaldoServicio respaldos;
    private final RespaldoProgramador programador;
    private final AuditoriaServicio auditoria;
    private final TransactionTemplate transaccion;

    /** Configuración vigente, último intento y archivos guardados. */
    public record EstadoRespaldos(boolean habilitado, List<String> horas, String zona, int retencionDias,
                                  Instant proximaEjecucion, Resultado ultimo, List<Archivo> archivos) {
    }

    @Operation(summary = "Estado del respaldo programado y lista de respaldos guardados")
    @GetMapping
    public ResponseEntity<RespuestaApi<EstadoRespaldos>> estado() {
        return RespuestaApi.ok(new EstadoRespaldos(programador.habilitado(),
                programador.horas().stream().map(LocalTime::toString).toList(), respaldos.zona().getId(),
                respaldos.retencionDias(), programador.proxima().map(z -> z.toInstant()).orElse(null), respaldos.ultimo(),
                respaldos.listar()), "Respaldos");
    }

    @Operation(summary = "Crear un respaldo ahora (queda auditado)")
    @PostMapping
    @LimitarPeticiones(porMinuto = 2)
    public ResponseEntity<RespuestaApi<Resultado>> crear() {
        Resultado resultado = respaldos.crear();
        transaccion.executeWithoutResult(estado -> auditoria.registrar(null, AccionAuditoria.SEGURIDAD_RESPALDO_MANUAL,
                "Respaldos", null, null, Map.of("archivo", resultado.nombre(), "bytes", resultado.bytes())));
        return RespuestaApi.creado(resultado, "Respaldo creado");
    }
}
