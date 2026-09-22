package shopify.api.core.modules.tienda.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.tienda.application.dto.request.CrearTiendaRequest;
import shopify.api.core.modules.tienda.domain.modelo.Tienda;
import shopify.api.core.modules.tienda.domain.puerto.salida.TiendaRepositorioPuerto;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.ReglaNegocioException;
import shopify.api.core.shared.seguridad.servicio.SeguridadUtil;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CrearTiendaCasoUso {

    private final TiendaRepositorioPuerto tiendaRepositorio;

    @Transactional
    public EstadoOperacionDto ejecutar(CrearTiendaRequest request) {
        UUID propietarioId = SeguridadUtil.obtenerUsuarioIdActual();
        String subdominioLimpio = request.getSubdominio().trim().toLowerCase(Locale.ROOT);

        if (tiendaRepositorio.existePorSubdominio(subdominioLimpio)) {
            throw new ReglaNegocioException("El subdominio '" + subdominioLimpio + "' ya se encuentra registrado");
        }

        Tienda nuevaTienda = Tienda.builder()
                .propietarioId(propietarioId)
                .nombre(request.getNombre().trim())
                .subdominio(subdominioLimpio)
                .moneda(request.getMoneda() != null ? request.getMoneda().toUpperCase(Locale.ROOT) : "USD")
                .correoContacto(request.getCorreoContacto().trim().toLowerCase(Locale.ROOT))
                .estaActiva(true)
                .build();

        Tienda guardada = tiendaRepositorio.guardar(nuevaTienda);

        return EstadoOperacionDto.creado(
                guardada.getId(),
                guardada.getSubdominio(),
                "Tienda creada exitosamente"
        );
    }
}