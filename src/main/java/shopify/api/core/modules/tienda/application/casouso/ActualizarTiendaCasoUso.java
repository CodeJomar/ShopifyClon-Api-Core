package shopify.api.core.modules.tienda.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.tienda.application.dto.request.ActualizarTiendaRequest;
import shopify.api.core.modules.tienda.domain.modelo.Tienda;
import shopify.api.core.modules.tienda.domain.puerto.salida.TiendaRepositorioPuerto;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;
import shopify.api.core.shared.exception.ReglaNegocioException;
import shopify.api.core.shared.seguridad.servicio.SeguridadUtil;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActualizarTiendaCasoUso {

    private final TiendaRepositorioPuerto tiendaRepositorio;

    @Transactional
    public EstadoOperacionDto ejecutar(UUID id, ActualizarTiendaRequest request) {
        UUID usuarioActual = SeguridadUtil.obtenerUsuarioIdActual();

        Tienda tienda = tiendaRepositorio.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tienda no encontrada con ID: " + id));

        if (!tienda.getPropietarioId().equals(usuarioActual)) {
            throw new ReglaNegocioException("No tienes permisos para modificar esta tienda");
        }

        tienda.setNombre(request.getNombre().trim());
        tienda.setDominioPersonalizado(request.getDominioPersonalizado());
        tienda.setCorreoContacto(request.getCorreoContacto().trim().toLowerCase(Locale.ROOT));

        if (request.getMoneda() != null) {
            tienda.setMoneda(request.getMoneda().toUpperCase(Locale.ROOT));
        }
        if (request.getEstaActiva() != null) {
            tienda.setEstaActiva(request.getEstaActiva());
        }

        tiendaRepositorio.guardar(tienda);

        return EstadoOperacionDto.exitoso(id, "Tienda actualizada exitosamente");
    }
}