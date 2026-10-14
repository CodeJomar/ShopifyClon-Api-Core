package shopify.api.core.modulos.envio.servicio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.envio.dto.request.TarifaEnvioRequest;
import shopify.api.core.modulos.envio.dto.response.TarifaEnvioResponse;
import shopify.api.core.modulos.envio.dto.response.TarifaVitrinaResponse;
import shopify.api.core.modulos.envio.entidad.TarifaEnvioEntidad;
import shopify.api.core.modulos.envio.mapeador.TarifaEnvioMapeador;
import shopify.api.core.modulos.envio.modelo.TarifaAplicable;
import shopify.api.core.modulos.envio.repositorio.TarifaEnvioRepositorio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/** Tarifas de envío de la tienda (ENV-001, ENV-002). */
@Service
@RequiredArgsConstructor
public class TarifaEnvioServicio {

    private final TarifaEnvioRepositorio repositorio;
    private final TarifaEnvioMapeador mapeador;
    private final TiendaServicio tiendaServicio;

    @Transactional(readOnly = true)
    public List<TarifaEnvioResponse> listar(ContextoTienda contexto) {
        return mapeador.aResponses(repositorio.findByTiendaIdAndEliminadoFalseOrderByOrdenAscNombreAsc(contexto.tiendaId()));
    }

    @Transactional
    public TarifaEnvioResponse crear(ContextoTienda contexto, TarifaEnvioRequest request) {
        TarifaEnvioEntidad tarifa = new TarifaEnvioEntidad();
        tarifa.setTiendaId(contexto.tiendaId());
        aplicar(tarifa, request, request.estaActiva() == null || request.estaActiva(),
                request.orden() == null ? 0 : request.orden());
        return mapeador.aResponse(repositorio.saveAndFlush(tarifa));
    }

    /** Si {@code esta_activa} viene ausente se conserva el valor actual; {@code orden} ausente vuelve a 0. */
    @Transactional
    public TarifaEnvioResponse actualizar(ContextoTienda contexto, UUID id, TarifaEnvioRequest request) {
        TarifaEnvioEntidad tarifa = buscarPropia(contexto, id);
        aplicar(tarifa, request,
                request.estaActiva() == null ? tarifa.getEstaActiva() : request.estaActiva(),
                request.orden() == null ? 0 : request.orden());
        return mapeador.aResponse(repositorio.saveAndFlush(tarifa));
    }

    /** Eliminación lógica. */
    @Transactional
    public void eliminar(ContextoTienda contexto, UUID id) {
        TarifaEnvioEntidad tarifa = buscarPropia(contexto, id);
        tarifa.setEliminado(true);
        repositorio.save(tarifa);
    }

    /** Vitrina: tarifas activas y no eliminadas de una tienda operativa (TIE-004); si no lo es, TIE-404. */
    @Transactional(readOnly = true)
    public List<TarifaVitrinaResponse> listarActivasPorSubdominio(String subdominio) {
        UUID tiendaId = tiendaServicio.idOperativaPorSubdominio(subdominio)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));
        return mapeador.aVitrinas(
                repositorio.findByTiendaIdAndEstaActivaTrueAndEliminadoFalseOrderByOrdenAscNombreAsc(tiendaId));
    }

    /** Para el checkout: la tarifa debe existir, estar activa y ser de la tienda; si no, ENV-001. */
    @Transactional(readOnly = true)
    public TarifaAplicable buscarActiva(UUID tiendaId, UUID tarifaId) {
        return repositorio.findByIdAndTiendaIdAndEstaActivaTrueAndEliminadoFalse(tarifaId, tiendaId)
                .map(tarifa -> new TarifaAplicable(tarifa.getId(), tarifa.getNombre(), tarifa.getPrecio(),
                        tarifa.getMontoMinimoGratis()))
                .orElseThrow(() -> new ConflictoEstadoException("ENV-001",
                        "El método de envío elegido no está disponible"));
    }

    /** Para el checkout: si no hay tarifas activas y la orden requiere envío, ENV-001. */
    @Transactional(readOnly = true)
    public boolean tieneTarifasActivas(UUID tiendaId) {
        return repositorio.existsByTiendaIdAndEstaActivaTrueAndEliminadoFalse(tiendaId);
    }

    private TarifaEnvioEntidad buscarPropia(ContextoTienda contexto, UUID id) {
        return repositorio.findByIdAndTiendaIdAndEliminadoFalse(id, contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("ENV-404", "No encontramos esa tarifa de envío"));
    }

    private static void aplicar(TarifaEnvioEntidad tarifa, TarifaEnvioRequest request, boolean activa, int orden) {
        tarifa.setNombre(request.nombre().strip());
        tarifa.setDescripcion(request.descripcion() == null || request.descripcion().isBlank()
                ? null : request.descripcion().strip());
        tarifa.setPrecio(Dinero.redondear(request.precio()));
        tarifa.setMontoMinimoGratis(request.montoMinimoGratis() == null
                ? null : Dinero.redondear(request.montoMinimoGratis()));
        tarifa.setEstaActiva(activa);
        tarifa.setOrden(orden);
    }
}
