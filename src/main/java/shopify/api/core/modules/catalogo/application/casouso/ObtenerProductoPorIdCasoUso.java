package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.application.dto.response.ProductoDetalleResponse;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.ImagenProductoEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.ProductoEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio.ProductoJpaRepositorio;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObtenerProductoPorIdCasoUso {

    private final ProductoJpaRepositorio productoRepositorio;

    @Transactional(readOnly = true)
    public ProductoDetalleResponse ejecutar(UUID id) {
        ProductoEntidad p = productoRepositorio.findByIdAndEliminadoFalse(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con ID: " + id));

        var variantes = p.getVariantes().stream()
                .filter(v -> !v.getEliminado())
                .map(v -> ProductoDetalleResponse.VarianteResponse.builder()
                        .id(v.getId())
                        .titulo(v.getTitulo())
                        .sku(v.getSku())
                        .precio(v.getPrecio())
                        .precioComparacion(v.getPrecioComparacion())
                        .stockDisponible(v.getInventario() != null ? v.getInventario().getCantidadDisponible() : 0)
                        .permitirVentaSinStock(v.getInventario() != null && v.getInventario().getPermitirVentaSinStock())
                        .build())
                .toList();

        var fotos = p.getImagenes().stream()
                .filter(img -> !img.getEliminado())
                .map(ImagenProductoEntidad::getUrlImagen)
                .toList();

        return ProductoDetalleResponse.builder()
                .id(p.getId())
                .tiendaId(p.getTiendaId())
                .titulo(p.getTitulo())
                .slug(p.getIdentificadorUrl())
                .descripcion(p.getDescripcion())
                .proveedor(p.getProveedor())
                .esDigital(p.getEsDigital())
                .estaPublicado(p.getEstaPublicado())
                .fechaCreacion(p.getFechaCreacion())
                .variantes(variantes)
                .imagenes(fotos)
                .build();
    }
}