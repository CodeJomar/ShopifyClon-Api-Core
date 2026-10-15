package shopify.api.core.modulos.tienda.servicio;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.tienda.modelo.DatosContactoTienda;
import shopify.api.core.modulos.tienda.repositorio.TiendaRepositorio;

/** Lectura de contacto de la tienda para las notificaciones. Solo lectura. */
@Service
@RequiredArgsConstructor
public class TiendaContactoServicio {

    private final TiendaRepositorio tiendas;

    @Transactional(readOnly = true)
    public DatosContactoTienda contacto(UUID tiendaId) {
        var tienda = tiendas.findByIdAndEliminadoFalse(tiendaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));
        return new DatosContactoTienda(tienda.getId(), tienda.getNombre(), tienda.getSubdominio(), tienda.getMoneda(),
                tienda.getCorreoContacto(), tiendas.correoDelPropietario(tiendaId).orElse(null));
    }
}
