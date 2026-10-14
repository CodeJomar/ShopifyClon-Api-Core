package shopify.api.core.modulos.inventario.servicio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.modulos.inventario.dto.request.AjusteInventarioRequest;
import shopify.api.core.modulos.inventario.dto.request.ConfiguracionInventarioRequest;
import shopify.api.core.modulos.inventario.dto.response.InventarioVarianteResponse;
import shopify.api.core.modulos.inventario.dto.response.MovimientoInventarioResponse;
import shopify.api.core.modulos.inventario.entidad.MovimientoInventarioEntidad;
import shopify.api.core.modulos.inventario.modelo.Cantidades;

/** Panel de inventario (INVENTARIO_AJUSTAR). Los cambios de cantidades pasan por {@link InventarioServicio}. */
@Service
@RequiredArgsConstructor
public class InventarioPanelServicio {

    static final int TAMANO_PAGINA = 20;

    private final InventarioServicio inventario;

    @Transactional
    public InventarioVarianteResponse ajustar(ContextoTienda contexto, UUID varianteId, AjusteInventarioRequest request) {
        inventario.ajustar(contexto.tiendaId(), varianteId, request.nuevaCantidad(), request.motivo().strip());
        return aResponse(varianteId, inventario.detalle(contexto.tiendaId(), varianteId));
    }

    @Transactional
    public InventarioVarianteResponse configurar(ContextoTienda contexto, UUID varianteId,
                                                 ConfiguracionInventarioRequest request) {
        inventario.configurar(contexto.tiendaId(), varianteId, request.rastreaInventario(), request.permitirVentaSinStock());
        return aResponse(varianteId, inventario.detalle(contexto.tiendaId(), varianteId));
    }

    @Transactional(readOnly = true)
    public InventarioVarianteResponse detalle(ContextoTienda contexto, UUID varianteId) {
        return aResponse(varianteId, inventario.detalle(contexto.tiendaId(), varianteId));
    }

    /** Más recientes primero, 20 por página; {@code desde} y {@code hasta} son opcionales. */
    @Transactional(readOnly = true)
    public Page<MovimientoInventarioResponse> movimientos(ContextoTienda contexto, UUID varianteId, int pagina,
                                                          Instant desde, Instant hasta) {
        Page<MovimientoInventarioEntidad> historial = inventario.movimientos(contexto.tiendaId(), varianteId, desde, hasta,
                PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "fechaCreacion")));
        return historial.map(m -> new MovimientoInventarioResponse(m.getId(), m.getTipo().name(), m.getDeltaDisponible(),
                m.getDeltaReservado(), m.getDisponibleResultante(), m.getReservadoResultante(), m.getOrdenId(),
                m.getMotivo(), m.getFechaCreacion()));
    }

    private static InventarioVarianteResponse aResponse(UUID varianteId, Cantidades cantidades) {
        return new InventarioVarianteResponse(varianteId, cantidades.disponible(), cantidades.reservada(),
                cantidades.disponible() - cantidades.reservada(), cantidades.rastrea(), cantidades.ventaSinStock());
    }
}
