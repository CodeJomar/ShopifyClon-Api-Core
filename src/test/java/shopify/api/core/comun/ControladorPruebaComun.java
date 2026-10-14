package shopify.api.core.comun;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.NoAutenticadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.comun.seguridad.SoloAdministradorPlataforma;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Controlador que solo existe en pruebas, para ejercitar el núcleo {@code comun} por HTTP. */
@RestController
@RequestMapping("/prueba-comun")
@RequiredArgsConstructor
public class ControladorPruebaComun {

    private final ServicioAuditoriaPrueba servicioAuditoria;
    private final AuditoriaServicio auditoriaServicio;

    @GetMapping("/panel")
    @RequierePermiso(Permiso.CUPONES_GESTIONAR)
    public ResponseEntity<RespuestaApi<Map<String, String>>> panel(ContextoTienda contexto) {
        return RespuestaApi.ok(Map.of("rol", contexto.rol().name(), "tienda_id", contexto.tiendaId().toString()), "ok");
    }

    @GetMapping("/panel-sin-permiso")
    public ResponseEntity<RespuestaApi<Map<String, String>>> panelSinPermiso(ContextoTienda contexto) {
        return RespuestaApi.ok(Map.of("rol", contexto.rol().name()), "ok");
    }

    @GetMapping("/plataforma")
    @SoloAdministradorPlataforma
    public ResponseEntity<RespuestaApi<String>> plataforma() {
        return RespuestaApi.ok("plataforma", "ok");
    }

    @GetMapping("/error/{tipo}")
    public ResponseEntity<RespuestaApi<Void>> error(@PathVariable String tipo) {
        switch (tipo) {
            case "solicitud" -> throw new SolicitudInvalidaException("PRU-400", "Solicitud inválida de prueba");
            case "no-autenticado" -> throw new NoAutenticadoException("PRU-401", "No autenticado de prueba");
            case "acceso" -> throw new AccesoDenegadoException("PRU-403", "Acceso denegado de prueba");
            case "no-encontrado" -> throw new RecursoNoEncontradoException("PRU-404", "No encontrado de prueba");
            case "conflicto" -> throw new ConflictoEstadoException("PRU-409", "Conflicto de prueba",
                    Map.of("disponible", 3, "solicitado", 5));
            case "regla" -> throw new ReglaNegocioException("PRU-422", "Regla de prueba");
            default -> throw new IllegalStateException("detalle-interno-secreto");
        }
    }

    @PostMapping("/validar")
    public ResponseEntity<RespuestaApi<String>> validar(@Valid @RequestBody CuerpoValidar cuerpo) {
        return RespuestaApi.ok(cuerpo.getNombreCampo(), "ok");
    }

    @PostMapping("/auditar")
    public ResponseEntity<RespuestaApi<Void>> auditar() {
        servicioAuditoria.auditar();
        return RespuestaApi.ok(null, "ok");
    }

    @PostMapping("/auditar-sin-transaccion")
    public ResponseEntity<RespuestaApi<Void>> auditarSinTransaccion() {
        auditoriaServicio.registrar(null, AccionAuditoria.TIENDA_CREADA, "Tiendas", UUID.randomUUID(), "sin transacción", null);
        return RespuestaApi.ok(null, "ok");
    }

    @Getter
    @Setter
    public static class CuerpoValidar {
        @NotBlank(message = "El campo es obligatorio")
        private String nombreCampo;
    }

    @Service
    @RequiredArgsConstructor
    public static class ServicioAuditoriaPrueba {

        private final AuditoriaServicio auditoriaServicio;

        @Transactional
        public void auditar() {
            auditoriaServicio.registrar(null, AccionAuditoria.INVENTARIO_AJUSTADO, "InventarioVariante",
                    UUID.fromString("00000000-0000-0000-0000-00000000a0d1"), "motivo de prueba",
                    Map.of("antes", Map.of("cantidad", 5), "despues", Map.of("cantidad", 8)));
        }
    }
}
