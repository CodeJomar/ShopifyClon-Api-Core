package shopify.api.core.modulos.suscripcion.servicio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.suscripcion.dto.request.ActualizarPlanRequest;
import shopify.api.core.modulos.suscripcion.dto.request.CrearPlanRequest;
import shopify.api.core.modulos.suscripcion.dto.response.PlanPlataformaResponse;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;
import shopify.api.core.modulos.suscripcion.mapeador.PlanPlataformaMapeador;
import shopify.api.core.modulos.suscripcion.repositorio.PlanSuscripcionRepositorio;

/** Catálogo de planes para la plataforma (SUS-003, SUS-013). Solo lo usa el administrador de plataforma. */
@Service
@RequiredArgsConstructor
public class PlataformaPlanServicio {

    private static final String ENTIDAD = "PlanesSuscripcion";
    private static final int DIAS_PRUEBA_POR_DEFECTO = 14;

    private final PlanSuscripcionRepositorio planes;
    private final PlanPlataformaMapeador mapeador;
    private final AuditoriaServicio auditoria;

    @Transactional(readOnly = true)
    public List<PlanPlataformaResponse> listar() {
        return mapeador.aResponses(planes.findByEliminadoFalseOrderByOrdenAscCodigoAsc());
    }

    @Transactional
    public PlanPlataformaResponse crear(CrearPlanRequest request) {
        if (planes.existsByCodigo(request.codigo())) {
            throw codigoDuplicado();
        }
        PlanSuscripcionEntidad plan = new PlanSuscripcionEntidad();
        plan.setCodigo(request.codigo());
        aplicar(plan, request.nombre(), request.descripcion(), request.precioMensual(), request.precioAnual(),
                request.moneda(), request.limiteProductos(), request.limitePersonal(), request.porcentajeComision(),
                request.diasPrueba(), request.esPublico(), request.orden());
        try {
            return mapeador.aResponse(planes.saveAndFlush(plan));
        } catch (DataIntegrityViolationException ex) {
            // Carrera: UqPlanesSuscripcionCodigo
            throw codigoDuplicado();
        }
    }

    /** El código no cambia. Si cambia un precio se audita; los períodos en curso no se tocan (SUS-003). */
    @Transactional
    public PlanPlataformaResponse actualizar(String codigo, ActualizarPlanRequest request) {
        PlanSuscripcionEntidad plan = buscar(codigo);
        Map<String, Object> antes = Map.of("precio_mensual", plan.getPrecioMensual(), "precio_anual", plan.getPrecioAnual());
        boolean cambiaPrecio = plan.getPrecioMensual().compareTo(Dinero.redondear(request.precioMensual())) != 0
                || plan.getPrecioAnual().compareTo(Dinero.redondear(request.precioAnual())) != 0;

        aplicar(plan, request.nombre(), request.descripcion(), request.precioMensual(), request.precioAnual(),
                request.moneda(), request.limiteProductos(), request.limitePersonal(), request.porcentajeComision(),
                request.diasPrueba(), request.esPublico(), request.orden());

        if (cambiaPrecio) {
            auditoria.registrar(null, AccionAuditoria.PLAN_PRECIO_CAMBIADO, ENTIDAD, plan.getId(), null,
                    Map.of("antes", antes,
                            "despues", Map.of("precio_mensual", plan.getPrecioMensual(), "precio_anual", plan.getPrecioAnual())));
        }
        return mapeador.aResponse(planes.saveAndFlush(plan));
    }

    /** SUS-013: nunca borra; deja el plan fuera del catálogo público. */
    @Transactional
    public void desactivar(String codigo) {
        PlanSuscripcionEntidad plan = buscar(codigo);
        plan.setEsPublico(false);
        planes.save(plan);
    }

    private PlanSuscripcionEntidad buscar(String codigo) {
        return planes.findByCodigoAndEliminadoFalse(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "No encontramos ese plan"));
    }

    private static void aplicar(PlanSuscripcionEntidad plan, String nombre, String descripcion, BigDecimal precioMensual,
                                BigDecimal precioAnual, String moneda, Integer limiteProductos, Integer limitePersonal,
                                BigDecimal porcentajeComision, Integer diasPrueba, Boolean esPublico, Integer orden) {
        plan.setNombre(nombre.strip());
        plan.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion.strip());
        plan.setPrecioMensual(Dinero.redondear(precioMensual));
        plan.setPrecioAnual(Dinero.redondear(precioAnual));
        plan.setMoneda(moneda);
        plan.setLimiteProductos(limiteProductos);
        plan.setLimitePersonal(limitePersonal);
        plan.setPorcentajeComision(porcentajeComision.setScale(4, Dinero.REDONDEO));
        plan.setDiasPrueba(diasPrueba == null ? DIAS_PRUEBA_POR_DEFECTO : diasPrueba);
        plan.setEsPublico(esPublico == null || esPublico);
        plan.setOrden(orden == null ? 0 : orden);
    }

    private static ReglaNegocioException codigoDuplicado() {
        return new ReglaNegocioException("VAL-001", "Ya existe un plan con ese código");
    }
}
