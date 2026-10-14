package shopify.api.core.modulos.catalogo.mapeador;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import shopify.api.core.modulos.catalogo.dto.response.CategoriaResponse;
import shopify.api.core.modulos.catalogo.dto.response.ProductoDetalleResponse;
import shopify.api.core.modulos.catalogo.dto.response.ProductoResumenResponse;
import shopify.api.core.modulos.catalogo.entidad.CategoriaEntidad;
import shopify.api.core.modulos.catalogo.entidad.ImagenProductoEntidad;
import shopify.api.core.modulos.catalogo.entidad.ProductoEntidad;
import shopify.api.core.modulos.catalogo.entidad.VarianteProductoEntidad;
import shopify.api.core.modulos.inventario.modelo.Cantidades;

/** Entidades a respuestas del panel. Los costos solo salen aquí, en el detalle del panel (CAT-012). */
@Component
public class CatalogoMapeador {

    public CategoriaResponse categoria(CategoriaEntidad categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre(), categoria.getIdentificadorUrl(),
                categoria.getDescripcion());
    }

    public ProductoDetalleResponse.CategoriaRef categoriaRef(CategoriaEntidad categoria) {
        return new ProductoDetalleResponse.CategoriaRef(categoria.getId(), categoria.getNombre(),
                categoria.getIdentificadorUrl());
    }

    /** Stock vendible = disponible − reservado, solo de las variantes que rastrean inventario (INV-001). */
    public ProductoResumenResponse resumen(ProductoEntidad producto, List<VarianteProductoEntidad> variantes,
                                           Map<UUID, Cantidades> cantidades, String imagenPrincipal) {
        BigDecimal precioMinimo = variantes.stream()
                .map(VarianteProductoEntidad::getPrecio)
                .min(Comparator.naturalOrder())
                .orElse(null);
        long stock = variantes.stream()
                .map(v -> cantidades.get(v.getId()))
                .filter(c -> c != null && c.rastrea())
                .mapToLong(c -> c.disponible() - c.reservada())
                .sum();
        return new ProductoResumenResponse(producto.getId(), producto.getTitulo(), producto.getIdentificadorUrl(),
                producto.getEstaPublicado(), precioMinimo, variantes.size(), stock, imagenPrincipal);
    }

    public ProductoDetalleResponse detalle(ProductoEntidad producto, List<ProductoDetalleResponse.CategoriaRef> categorias,
                                           List<VarianteProductoEntidad> variantes, Map<UUID, Cantidades> cantidades,
                                           List<ImagenProductoEntidad> imagenes) {
        return new ProductoDetalleResponse(
                producto.getId(),
                producto.getTitulo(),
                producto.getIdentificadorUrl(),
                producto.getDescripcion(),
                producto.getProveedor(),
                producto.getEsDigital(),
                producto.getEstaPublicado(),
                categorias,
                variantes.stream().map(v -> variante(v, cantidades.get(v.getId()))).toList(),
                imagenes.stream().map(this::imagen).toList());
    }

    public ProductoDetalleResponse.Variante variante(VarianteProductoEntidad variante, Cantidades cantidades) {
        ProductoDetalleResponse.Inventario inventario = cantidades == null
                ? new ProductoDetalleResponse.Inventario(0, 0, false, false)
                : new ProductoDetalleResponse.Inventario(cantidades.disponible(), cantidades.reservada(),
                cantidades.rastrea(), cantidades.ventaSinStock());
        return new ProductoDetalleResponse.Variante(variante.getId(), variante.getTitulo(), variante.getSku(),
                variante.getCodigoBarras(), variante.getPrecio(), variante.getPrecioComparacion(),
                variante.getCostoUnitario(), variante.getPesoGramos(), variante.getRequiereEnvio(), inventario);
    }

    public ProductoDetalleResponse.Imagen imagen(ImagenProductoEntidad imagen) {
        return new ProductoDetalleResponse.Imagen(imagen.getId(), imagen.getUrlImagen(), imagen.getTextoAlternativo(),
                imagen.getVarianteId(), imagen.getPosicion(), imagen.getEsPrincipal());
    }
}
