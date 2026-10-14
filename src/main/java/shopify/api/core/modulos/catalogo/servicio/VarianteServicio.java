package shopify.api.core.modulos.catalogo.servicio;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.catalogo.dto.request.VarianteEdicionRequest;
import shopify.api.core.modulos.catalogo.dto.request.VarianteRequest;
import shopify.api.core.modulos.catalogo.dto.response.ProductoDetalleResponse;
import shopify.api.core.modulos.catalogo.entidad.ProductoEntidad;
import shopify.api.core.modulos.catalogo.entidad.VarianteProductoEntidad;
import shopify.api.core.modulos.catalogo.mapeador.CatalogoMapeador;
import shopify.api.core.modulos.catalogo.repositorio.ProductoRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.VarianteProductoRepositorio;
import shopify.api.core.modulos.inventario.modelo.Cantidades;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;

/** Variantes de un producto: alta con inventario inicial, edición y eliminación (CAT-001, CAT-003 a CAT-005). */
@Service
@RequiredArgsConstructor
public class VarianteServicio {

    private final VarianteProductoRepositorio variantes;
    private final ProductoRepositorio productos;
    private final InventarioServicio inventario;
    private final VarianteReglas reglas;
    private final CatalogoMapeador mapeador;

    @Transactional
    public ProductoDetalleResponse.Variante agregar(ContextoTienda contexto, UUID productoId, VarianteRequest request) {
        ProductoEntidad producto = productos.findByIdAndTiendaIdAndEliminadoFalse(productoId, contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("CAT-404", "No encontramos ese producto"));
        String sku = reglas.normalizarSku(contexto.tiendaId(), request.sku(), null);
        VarianteProductoEntidad variante = VarianteReglas.nueva(producto.getId(), request, sku, VarianteReglas.esDigital(producto));
        variantes.saveAndFlush(variante);
        inventario.crearInicial(contexto.tiendaId(), variante.getId(),
                request.cantidadInicial() == null ? 0 : request.cantidadInicial(),
                request.rastreaInventario() == null || request.rastreaInventario(),
                Boolean.TRUE.equals(request.permitirVentaSinStock()));
        return mapeador.variante(variante, cantidades(variante.getId()));
    }

    @Transactional
    public ProductoDetalleResponse.Variante actualizar(ContextoTienda contexto, UUID id, VarianteEdicionRequest request) {
        VarianteProductoEntidad variante = buscar(contexto, id);
        ProductoEntidad producto = productos.findById(variante.getProductoId()).orElseThrow();
        String sku = reglas.normalizarSku(contexto.tiendaId(), request.sku(), id);
        VarianteReglas.aplicar(variante, request.titulo(), request.codigoBarras(), request.precio(),
                request.precioComparacion(), request.costoUnitario(), request.pesoGramos(), request.requiereEnvio(),
                sku, VarianteReglas.esDigital(producto));
        variantes.saveAndFlush(variante);
        return mapeador.variante(variante, cantidades(id));
    }

    /** CAT-001: un producto necesita al menos una variante activa. */
    @Transactional
    public void eliminar(ContextoTienda contexto, UUID id) {
        VarianteProductoEntidad variante = buscar(contexto, id);
        if (variantes.countByProductoIdAndEliminadoFalse(variante.getProductoId()) <= 1) {
            throw new ReglaNegocioException("CAT-001", "Un producto necesita al menos una variante");
        }
        variante.setEliminado(true);
        variantes.saveAndFlush(variante);
    }

    /** SEG-004: una variante de otra tienda no existe para esta. */
    VarianteProductoEntidad buscar(ContextoTienda contexto, UUID id) {
        return variantes.buscarDeTienda(id, contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("CAT-404", "No encontramos esa variante"));
    }

    private Cantidades cantidades(UUID varianteId) {
        return inventario.cantidades(java.util.List.of(varianteId)).get(varianteId);
    }
}
