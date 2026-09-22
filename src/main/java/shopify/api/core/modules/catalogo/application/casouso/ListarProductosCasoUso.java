package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.application.dto.request.ProductoQueryInput;
import shopify.api.core.modules.catalogo.application.dto.response.ProductoResumenResponse;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.ImagenProductoEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.InventarioVarianteEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.ProductoEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.VarianteProductoEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio.ProductoJpaRepositorio;
import shopify.api.core.shared.dto.ConsultaPaginadaDto;
import shopify.api.core.shared.multitenancy.TenantContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListarProductosCasoUso {

    private final ProductoJpaRepositorio productoRepositorio;

    @Transactional(readOnly = true)
    public ConsultaPaginadaDto<ProductoResumenResponse> ejecutar(ProductoQueryInput input) {
        UUID tiendaId = TenantContextHolder.getTenantId();

        Sort.Direction dir = "ASC".equalsIgnoreCase(input.getDireccionOrden())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        PageRequest pageRequest = PageRequest.of(input.getPagina(), input.getTamano(), Sort.by(dir, "fechaCreacion"));

        Page<ProductoEntidad> pagina = productoRepositorio.buscarConFiltros(
                tiendaId, input.getEstaPublicado(), input.getTextoBusqueda(), pageRequest);

        Page<ProductoResumenResponse> mapa = pagina.map(this::mapearResumen);

        return ConsultaPaginadaDto.desdePagina(mapa);
    }

    private ProductoResumenResponse mapearResumen(ProductoEntidad p) {
        BigDecimal precioDesde = p.getVariantes().stream()
                .filter(v -> !v.getEliminado())
                .map(VarianteProductoEntidad::getPrecio)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        int stock = p.getVariantes().stream()
                .filter(v -> !v.getEliminado() && v.getInventario() != null)
                .map(VarianteProductoEntidad::getInventario)
                .mapToInt(InventarioVarianteEntidad::getCantidadDisponible)
                .sum();

        String fotoPrincipal = p.getImagenes().stream()
                .filter(img -> !img.getEliminado() && img.getEsPrincipal())
                .map(ImagenProductoEntidad::getUrlImagen)
                .findFirst()
                .orElse(p.getImagenes().isEmpty() ? null : p.getImagenes().get(0).getUrlImagen());

        return ProductoResumenResponse.builder()
                .id(p.getId())
                .titulo(p.getTitulo())
                .slug(p.getIdentificadorUrl())
                .proveedor(p.getProveedor())
                .estaPublicado(p.getEstaPublicado())
                .precioDesde(precioDesde)
                .stockTotal(stock)
                .imagenPrincipal(fotoPrincipal)
                .fechaCreacion(p.getFechaCreacion())
                .build();
    }
}