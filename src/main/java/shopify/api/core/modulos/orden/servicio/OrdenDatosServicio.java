package shopify.api.core.modulos.orden.servicio;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.websocket.VerificadorAccesoOrdenWs;
import shopify.api.core.modulos.orden.modelo.DatosNotificacionOrden;
import shopify.api.core.modulos.orden.modelo.DatosTiempoRealOrden;
import shopify.api.core.modulos.orden.repositorio.OrdenRepositorio;

/** Lectura de una orden para correos y notificaciones en tiempo real. Solo lectura. */
@Service
@RequiredArgsConstructor
public class OrdenDatosServicio implements VerificadorAccesoOrdenWs {

    private final OrdenRepositorio ordenes;

    @Transactional(readOnly = true)
    public DatosNotificacionOrden datosNotificacion(UUID ordenId) {
        var orden = ordenes.findFirstByIdAndEliminadoFalse(ordenId)
                .orElseThrow(() -> new RecursoNoEncontradoException("ORD-404", "No encontramos esa orden"));
        return new DatosNotificacionOrden(orden.getTiendaId(), orden.getNumeroOrden(), orden.getCorreoCliente(),
                orden.getNombreCliente(), orden.getTotal(), orden.getTokenAcceso());
    }

    @Transactional(readOnly = true)
    public Optional<DatosTiempoRealOrden> datosTiempoReal(UUID ordenId) {
        return ordenes.findFirstByIdAndEliminadoFalse(ordenId)
                .map(orden -> new DatosTiempoRealOrden(orden.getId(), orden.getTiendaId(), orden.getNumeroOrden(),
                        orden.getTokenAcceso(), orden.getNombreCliente(), orden.getTotal(), orden.getEstado(),
                        orden.getEstadoPago(), orden.getEstadoPreparacion()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeOrdenActivaPorTokenAcceso(String tokenAcceso) {
        return StringUtils.hasText(tokenAcceso) && ordenes.existsByTokenAccesoAndEliminadoFalse(tokenAcceso);
    }
}
