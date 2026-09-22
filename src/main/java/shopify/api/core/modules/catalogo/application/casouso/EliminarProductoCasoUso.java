package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.ProductoEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio.ProductoJpaRepositorio;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EliminarProductoCasoUso {

    private final ProductoJpaRepositorio productoRepositorio;

    @Transactional
    public EstadoOperacionDto ejecutar(UUID id) {
        ProductoEntidad producto = productoRepositorio.findByIdAndEliminadoFalse(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con ID: " + id));

        producto.setEliminado(true);
        producto.getVariantes().forEach(v -> v.setEliminado(true));
        productoRepositorio.save(producto);

        return EstadoOperacionDto.exitoso(id, "Producto eliminado exitosamente");
    }
}