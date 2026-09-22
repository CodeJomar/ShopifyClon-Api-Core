package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.application.dto.request.AjustarInventarioRequest;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.InventarioVarianteEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio.InventarioVarianteJpaRepositorio;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;
import shopify.api.core.shared.exception.ReglaNegocioException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AjustarStockVarianteCasoUso {

    private final InventarioVarianteJpaRepositorio inventarioRepositorio;

    @Transactional
    public EstadoOperacionDto ejecutar(UUID varianteId, AjustarInventarioRequest request) {
        if (request.getNuevaCantidadDisponible() < 0) {
            throw new ReglaNegocioException("El stock disponible no puede ser negativo");
        }

        InventarioVarianteEntidad inventario = inventarioRepositorio.findByVarianteIdAndEliminadoFalse(varianteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inventario no encontrado para la variante: " + varianteId));

        inventario.setCantidadDisponible(request.getNuevaCantidadDisponible());
        inventarioRepositorio.save(inventario);

        return EstadoOperacionDto.exitoso(varianteId, "Stock actualizado exitosamente a: " + request.getNuevaCantidadDisponible());
    }
}