package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.application.dto.request.ActualizarProductoRequest;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.ProductoEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio.ProductoJpaRepositorio;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActualizarProductoCasoUso {

    private final ProductoJpaRepositorio productoRepositorio;

    @Transactional
    public EstadoOperacionDto ejecutar(UUID id, ActualizarProductoRequest request) {
        ProductoEntidad producto = productoRepositorio.findByIdAndEliminadoFalse(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con ID: " + id));

        producto.setTitulo(request.getTitulo().trim());
        producto.setDescripcion(request.getDescripcion());
        producto.setProveedor(request.getProveedor());

        if (request.getEsDigital() != null) {
            producto.setEsDigital(request.getEsDigital());
        }
        if (request.getEstaPublicado() != null) {
            producto.setEstaPublicado(request.getEstaPublicado());
        }

        productoRepositorio.save(producto);

        return EstadoOperacionDto.exitoso(id, "Producto actualizado exitosamente");
    }
}