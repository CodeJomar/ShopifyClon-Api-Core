package shopify.api.core.modulos.balance.servicio;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modulos.balance.dto.response.RepartoResponse;
import shopify.api.core.modulos.balance.entidad.RepartoPagoEntidad;
import shopify.api.core.modulos.balance.repositorio.RepartoPagoRepositorio;

/**
 * Libro de repartos (R-001, B6): un asiento auditable por orden pagada con lo que le toca a su tienda. Las tiendas se
 * cobran después con el balance; no hay reparto real en la pasarela.
 */
@Service
@RequiredArgsConstructor
public class RepartoServicio {

    static final int TAMANO_PAGINA = 20;

    private final RepartoPagoRepositorio repartos;

    /** Un asiento de VENTA por orden: si ya existe, no hace nada (confirmaciones repetidas). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarVenta(UUID pedidoId, UUID ordenId, UUID tiendaId, BigDecimal bruto, BigDecimal comision,
                               String moneda) {
        if (repartos.existsByOrdenIdAndTipoAndEliminadoFalse(ordenId, "VENTA")) {
            return;
        }
        RepartoPagoEntidad reparto = new RepartoPagoEntidad();
        reparto.setPedidoId(pedidoId);
        reparto.setOrdenId(ordenId);
        reparto.setTiendaId(tiendaId);
        reparto.setBruto(bruto);
        reparto.setComision(comision);
        reparto.setNeto(bruto.subtract(comision));
        reparto.setMoneda(moneda);
        repartos.saveAndFlush(reparto);
    }

    @Transactional(readOnly = true)
    public Page<RepartoResponse> movimientosDeTienda(UUID tiendaId, int pagina) {
        return repartos.findByTiendaIdAndEliminadoFalseOrderByFechaCreacionDesc(tiendaId, pagina(pagina))
                .map(RepartoServicio::aRespuesta);
    }

    @Transactional(readOnly = true)
    public Page<RepartoResponse> deLaPlataforma(UUID pedidoId, int pagina) {
        Page<RepartoPagoEntidad> resultado = pedidoId == null
                ? repartos.findByEliminadoFalseOrderByFechaCreacionDesc(pagina(pagina))
                : repartos.findByPedidoIdAndEliminadoFalseOrderByFechaCreacionDesc(pedidoId, pagina(pagina));
        return resultado.map(RepartoServicio::aRespuesta);
    }

    private static PageRequest pagina(int pagina) {
        return PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA);
    }

    private static RepartoResponse aRespuesta(RepartoPagoEntidad r) {
        return new RepartoResponse(r.getId(), r.getPedidoId(), r.getOrdenId(), r.getTiendaId(), r.getTipo(), r.getBruto(),
                r.getComision(), r.getNeto(), r.getMoneda(), r.getFechaCreacion());
    }
}
