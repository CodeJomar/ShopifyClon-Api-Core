package shopify.api.core.modulos.orden.servicio;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.orden.modelo.DatosNotificacionOrden;
import shopify.api.core.modulos.orden.repositorio.OrdenRepositorio;

/** Lectura de una orden para los correos de notificación. Solo lectura. */
@Service
@RequiredArgsConstructor
public class OrdenDatosServicio {

    private final OrdenRepositorio ordenes;

    @Transactional(readOnly = true)
    public DatosNotificacionOrden datosNotificacion(UUID ordenId) {
        var orden = ordenes.findFirstByIdAndEliminadoFalse(ordenId)
                .orElseThrow(() -> new RecursoNoEncontradoException("ORD-404", "No encontramos esa orden"));
        return new DatosNotificacionOrden(orden.getTiendaId(), orden.getNumeroOrden(), orden.getCorreoCliente(),
                orden.getNombreCliente(), orden.getTotal(), orden.getTokenAcceso());
    }
}
