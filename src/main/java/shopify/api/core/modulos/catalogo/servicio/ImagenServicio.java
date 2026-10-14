package shopify.api.core.modulos.catalogo.servicio;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.modulos.catalogo.dto.request.ImagenRequest;
import shopify.api.core.modulos.catalogo.dto.response.ProductoDetalleResponse;
import shopify.api.core.modulos.catalogo.entidad.ImagenProductoEntidad;
import shopify.api.core.modulos.catalogo.entidad.ProductoEntidad;
import shopify.api.core.modulos.catalogo.mapeador.CatalogoMapeador;
import shopify.api.core.modulos.catalogo.repositorio.ImagenProductoRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.ProductoRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.VarianteProductoRepositorio;

/** Imágenes de un producto. Una sola principal por producto (CAT-010). */
@Service
@RequiredArgsConstructor
public class ImagenServicio {

    private final ImagenProductoRepositorio imagenes;
    private final ProductoRepositorio productos;
    private final VarianteProductoRepositorio variantes;
    private final CatalogoMapeador mapeador;

    /** La primera imagen es principal; marcar otra como principal desmarca la anterior. */
    @Transactional
    public ProductoDetalleResponse.Imagen agregar(ContextoTienda contexto, UUID productoId, ImagenRequest request) {
        ProductoEntidad producto = productos.findByIdAndTiendaIdAndEliminadoFalse(productoId, contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("CAT-404", "No encontramos ese producto"));
        exigirVarianteDelProducto(producto.getId(), request.varianteId());

        List<ImagenProductoEntidad> existentes = imagenes.findByProductoIdAndEliminadoFalseOrderByPosicionAscFechaCreacionAsc(producto.getId());
        boolean principal = Boolean.TRUE.equals(request.esPrincipal()) || existentes.isEmpty();
        if (principal) {
            imagenes.desmarcarPrincipales(producto.getId());
        }

        ImagenProductoEntidad imagen = new ImagenProductoEntidad();
        imagen.setProductoId(producto.getId());
        imagen.setVarianteId(request.varianteId());
        imagen.setUrlImagen(request.urlImagen().strip());
        imagen.setTextoAlternativo(textoOpcional(request.textoAlternativo()));
        imagen.setPosicion(request.posicion() == null ? existentes.size() + 1 : request.posicion());
        imagen.setEsPrincipal(principal);
        return mapeador.imagen(imagenes.saveAndFlush(imagen));
    }

    /** Marcar como principal desmarca la anterior antes de marcar la nueva (CAT-010). */
    @Transactional
    public ProductoDetalleResponse.Imagen actualizar(ContextoTienda contexto, UUID id, ImagenRequest request) {
        ImagenProductoEntidad imagen = buscar(contexto, id);
        exigirVarianteDelProducto(imagen.getProductoId(), request.varianteId());
        if (Boolean.TRUE.equals(request.esPrincipal())) {
            imagenes.desmarcarPrincipales(imagen.getProductoId());
            imagen = buscar(contexto, id);
        }
        imagen.setUrlImagen(request.urlImagen().strip());
        imagen.setTextoAlternativo(textoOpcional(request.textoAlternativo()));
        imagen.setVarianteId(request.varianteId());
        if (request.posicion() != null) {
            imagen.setPosicion(request.posicion());
        }
        if (Boolean.TRUE.equals(request.esPrincipal())) {
            imagen.setEsPrincipal(true);
        } else if (Boolean.FALSE.equals(request.esPrincipal())) {
            imagen.setEsPrincipal(false);
        }
        return mapeador.imagen(imagenes.saveAndFlush(imagen));
    }

    /** Al borrar la principal, la siguiente por posición pasa a serlo (CAT-010). */
    @Transactional
    public void eliminar(ContextoTienda contexto, UUID id) {
        ImagenProductoEntidad imagen = buscar(contexto, id);
        boolean eraPrincipal = Boolean.TRUE.equals(imagen.getEsPrincipal());
        imagen.setEliminado(true);
        imagen.setEsPrincipal(false);
        imagenes.saveAndFlush(imagen);
        if (eraPrincipal) {
            imagenes.findByProductoIdAndEliminadoFalseOrderByPosicionAscFechaCreacionAsc(imagen.getProductoId()).stream()
                    .findFirst()
                    .ifPresent(siguiente -> {
                        siguiente.setEsPrincipal(true);
                        imagenes.saveAndFlush(siguiente);
                    });
        }
    }

    private ImagenProductoEntidad buscar(ContextoTienda contexto, UUID id) {
        return imagenes.buscarDeTienda(id, contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("CAT-404", "No encontramos esa imagen"));
    }

    /** La variante de la imagen, si se indica, debe ser de ese mismo producto. */
    private void exigirVarianteDelProducto(UUID productoId, UUID varianteId) {
        if (varianteId != null && variantes.findByIdAndProductoIdAndEliminadoFalse(varianteId, productoId).isEmpty()) {
            throw new RecursoNoEncontradoException("CAT-404", "Esa variante no pertenece a este producto");
        }
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
