package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import shopify.api.core.modules.catalogo.application.dto.request.CrearProductoRequest;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.*;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio.*;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.ReglaNegocioException;
import shopify.api.core.shared.multitenancy.TenantContextHolder;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CrearProductoCasoUso {

    private final ProductoJpaRepositorio productoRepositorio;
    private final VarianteProductoJpaRepositorio varianteRepositorio;
    private final ProductoCategoriaJpaRepositorio productoCategoriaRepositorio;
    private static final Pattern NO_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Transactional
    public EstadoOperacionDto ejecutar(CrearProductoRequest request) {
        UUID tiendaId = TenantContextHolder.getTenantId();
        if (tiendaId == null) {
            tiendaId = request.getIdTienda();
        }
        if (tiendaId == null) {
            throw new ReglaNegocioException("Se requiere el encabezado X-Tienda-Id para crear productos");
        }

        String skuLimpio = request.getSku().trim().toUpperCase();
        if (varianteRepositorio.existsBySkuAndEliminadoFalse(skuLimpio)) {
            throw new ReglaNegocioException("Ya existe una variante registrada con el SKU: " + skuLimpio);
        }

        String slug = generarSlug(request.getTitulo().trim());
        if (productoRepositorio.existsByIdentificadorUrlAndTiendaIdAndEliminadoFalse(slug, tiendaId)) {
            slug = slug + "-" + System.currentTimeMillis() % 10000;
        }

        // 1. Crear Producto
        ProductoEntidad producto = new ProductoEntidad();
        producto.setTiendaId(tiendaId);
        producto.setTitulo(request.getTitulo().trim());
        producto.setIdentificadorUrl(slug);
        producto.setDescripcion(request.getDescripcion());
        producto.setProveedor(request.getProveedor());
        producto.setEsDigital(request.getEsDigital() != null ? request.getEsDigital() : false);
        producto.setEstaPublicado(request.getEstaPublicado() != null ? request.getEstaPublicado() : true);

        // 2. Crear Variante por Defecto
        VarianteProductoEntidad variante = new VarianteProductoEntidad();
        variante.setProducto(producto);
        variante.setTitulo("Por Defecto");
        variante.setSku(skuLimpio);
        variante.setPrecio(request.getPrecio());
        variante.setPrecioComparacion(request.getPrecioComparacion());
        variante.setCostoUnitario(request.getCostoUnitario());
        variante.setPesoGramos(request.getPesoGramos());
        variante.setRequiereEnvio(request.getRequiereEnvio() != null ? request.getRequiereEnvio() : true);

        // 3. Crear Inventario de la Variante
        InventarioVarianteEntidad inventario = new InventarioVarianteEntidad();
        inventario.setVariante(variante);
        inventario.setCantidadDisponible(request.getCantidadDisponible());
        inventario.setCantidadReservada(0);
        inventario.setRastreaInventario(true);
        inventario.setPermitirVentaSinStock(request.getPermitirVentaSinStock() != null ? request.getPermitirVentaSinStock() : false);
        variante.setInventario(inventario);

        producto.getVariantes().add(variante);

        // 4. Imagen si fue proporcionada
        if (StringUtils.hasText(request.getUrlImagenPrincipal())) {
            ImagenProductoEntidad imagen = new ImagenProductoEntidad();
            imagen.setProducto(producto);
            imagen.setUrlImagen(request.getUrlImagenPrincipal().trim());
            imagen.setEsPrincipal(true);
            imagen.setPosicion(1);
            producto.getImagenes().add(imagen);
        }

        ProductoEntidad guardado = productoRepositorio.save(producto);

        // 5. Vincular Categoría si se indicó
        if (request.getCategoriaId() != null) {
            ProductoCategoriaEntidad relacion = new ProductoCategoriaEntidad();
            relacion.setProductoId(guardado.getId());
            relacion.setCategoriaId(request.getCategoriaId());
            productoCategoriaRepositorio.save(relacion);
        }

        return EstadoOperacionDto.creado(
                guardado.getId(),
                guardado.getIdentificadorUrl(),
                "Producto creado exitosamente con variante por defecto e inventario"
        );
    }

    private String generarSlug(String entrada) {
        String sinEspacios = WHITESPACE.matcher(entrada).replaceAll("-");
        String normalizado = Normalizer.normalize(sinEspacios, Normalizer.Form.NFD);
        String slug = NO_LATIN.matcher(normalizado).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}