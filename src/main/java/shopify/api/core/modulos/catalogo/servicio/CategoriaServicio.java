package shopify.api.core.modulos.catalogo.servicio;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.modulos.catalogo.dto.request.CategoriaRequest;
import shopify.api.core.modulos.catalogo.dto.response.CategoriaResponse;
import shopify.api.core.modulos.catalogo.entidad.CategoriaEntidad;
import shopify.api.core.modulos.catalogo.mapeador.CatalogoMapeador;
import shopify.api.core.modulos.catalogo.repositorio.CategoriaRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.ProductoCategoriaRepositorio;

/** Categorías de la tienda (CAT-002, CAT-009). */
@Service
@RequiredArgsConstructor
public class CategoriaServicio {

    private static final int MAX_IDENTIFICADOR = 100;

    private final CategoriaRepositorio categorias;
    private final ProductoCategoriaRepositorio productoCategorias;
    private final CatalogoMapeador mapeador;

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(ContextoTienda contexto) {
        return categorias.findByTiendaIdAndEliminadoFalseOrderByNombreAsc(contexto.tiendaId()).stream()
                .map(mapeador::categoria)
                .toList();
    }

    @Transactional
    public CategoriaResponse crear(ContextoTienda contexto, CategoriaRequest request) {
        UUID tiendaId = contexto.tiendaId();
        CategoriaEntidad categoria = new CategoriaEntidad();
        categoria.setTiendaId(tiendaId);
        categoria.setIdentificadorUrl(identificador(tiendaId, request.identificadorUrl(), request.nombre(), null));
        aplicarDatos(categoria, request);
        return mapeador.categoria(categorias.saveAndFlush(categoria));
    }

    /** Cambiar el identificador sí se valida y no añade sufijo: debe ser único tal cual. */
    @Transactional
    public CategoriaResponse actualizar(ContextoTienda contexto, UUID id, CategoriaRequest request) {
        CategoriaEntidad categoria = buscar(contexto, id);
        if (request.identificadorUrl() != null && !request.identificadorUrl().isBlank()) {
            categoria.setIdentificadorUrl(identificador(contexto.tiendaId(), request.identificadorUrl(), null, id));
        }
        aplicarDatos(categoria, request);
        return mapeador.categoria(categorias.saveAndFlush(categoria));
    }

    /** Eliminación lógica; sus filas de ProductosCategorias se eliminan con ella. */
    @Transactional
    public void eliminar(ContextoTienda contexto, UUID id) {
        CategoriaEntidad categoria = buscar(contexto, id);
        categoria.setEliminado(true);
        categorias.saveAndFlush(categoria);
        productoCategorias.quitarCategoria(id);
    }

    private String identificador(UUID tiendaId, String escrito, String nombre, UUID excluirId) {
        if (escrito != null && !escrito.isBlank()) {
            return IdentificadoresUrl.validar(escrito, MAX_IDENTIFICADOR,
                    slug -> excluirId == null
                            ? categorias.existsByTiendaIdAndIdentificadorUrlAndEliminadoFalse(tiendaId, slug)
                            : categorias.existsByTiendaIdAndIdentificadorUrlAndEliminadoFalseAndIdNot(tiendaId, slug, excluirId));
        }
        return IdentificadoresUrl.generar(nombre == null ? "" : nombre, MAX_IDENTIFICADOR,
                slug -> categorias.existsByTiendaIdAndIdentificadorUrlAndEliminadoFalse(tiendaId, slug));
    }

    private static void aplicarDatos(CategoriaEntidad categoria, CategoriaRequest request) {
        categoria.setNombre(request.nombre().strip());
        categoria.setDescripcion(request.descripcion() == null || request.descripcion().isBlank()
                ? null : request.descripcion().strip());
    }

    /** CAT-009 y SEG-004: una categoría de otra tienda no existe para esta tienda. */
    CategoriaEntidad buscar(ContextoTienda contexto, UUID id) {
        return categorias.findByIdAndTiendaIdAndEliminadoFalse(id, contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("CAT-404", "No encontramos esa categoría"));
    }
}
