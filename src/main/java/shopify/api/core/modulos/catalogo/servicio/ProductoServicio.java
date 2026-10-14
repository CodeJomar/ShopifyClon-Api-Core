package shopify.api.core.modulos.catalogo.servicio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.catalogo.dto.request.ProductoDatosRequest;
import shopify.api.core.modulos.catalogo.dto.request.ProductoRequest;
import shopify.api.core.modulos.catalogo.dto.request.VarianteRequest;
import shopify.api.core.modulos.catalogo.dto.response.ProductoDetalleResponse;
import shopify.api.core.modulos.catalogo.dto.response.ProductoResumenResponse;
import shopify.api.core.modulos.catalogo.entidad.CategoriaEntidad;
import shopify.api.core.modulos.catalogo.entidad.ImagenProductoEntidad;
import shopify.api.core.modulos.catalogo.entidad.ProductoCategoriaEntidad;
import shopify.api.core.modulos.catalogo.entidad.ProductoEntidad;
import shopify.api.core.modulos.catalogo.entidad.VarianteProductoEntidad;
import shopify.api.core.modulos.catalogo.mapeador.CatalogoMapeador;
import shopify.api.core.modulos.catalogo.repositorio.CategoriaRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.ImagenProductoRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.ProductoCategoriaRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.ProductoRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.VarianteProductoRepositorio;
import shopify.api.core.modulos.inventario.modelo.Cantidades;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.modulos.suscripcion.servicio.UsoPlanServicio;

/** Productos del panel: alta con variantes, edición, publicación (CAT-007), categorías y eliminación lógica. */
@Service
@RequiredArgsConstructor
public class ProductoServicio {

    static final int TAMANO_PAGINA = 20;

    private static final int MAX_IDENTIFICADOR = 200;

    private final ProductoRepositorio productos;
    private final VarianteProductoRepositorio variantes;
    private final ImagenProductoRepositorio imagenes;
    private final CategoriaRepositorio categorias;
    private final ProductoCategoriaRepositorio productoCategorias;
    private final InventarioServicio inventario;
    private final UsoPlanServicio usoPlan;
    private final VarianteReglas reglas;
    private final CatalogoMapeador mapeador;

    @Transactional(readOnly = true)
    public Page<ProductoResumenResponse> listarPanel(ContextoTienda contexto, int pagina, String texto,
                                                     Boolean publicado, UUID categoriaId) {
        Page<ProductoEntidad> pagina0 = productos.buscarPanel(contexto.tiendaId(), patron(texto), publicado, categoriaId,
                PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "fechaCreacion")));
        List<ProductoEntidad> lote = pagina0.getContent();
        Map<UUID, List<VarianteProductoEntidad>> variantesPorProducto = agrupar(lote);
        Map<UUID, Cantidades> cantidades = inventario.cantidades(idsDeVariantes(variantesPorProducto));
        Map<UUID, String> imagenPrincipal = imagenesPrincipales(lote);
        return pagina0.map(producto -> mapeador.resumen(producto,
                variantesPorProducto.getOrDefault(producto.getId(), List.of()), cantidades,
                imagenPrincipal.get(producto.getId())));
    }

    @Transactional(readOnly = true)
    public ProductoDetalleResponse detalle(ContextoTienda contexto, UUID id) {
        return detalle(buscar(contexto, id));
    }

    /** Nace no publicado. Un producto digital fuerza {@code requiere_envio = false} en todas sus variantes (CAT-005). */
    @Transactional
    public ProductoDetalleResponse crear(ContextoTienda contexto, ProductoRequest request) {
        UUID tiendaId = contexto.tiendaId();
        usoPlan.exigirCupoProducto(tiendaId);
        boolean digital = Boolean.TRUE.equals(request.esDigital());
        List<CategoriaEntidad> categoriasElegidas = categoriasDeTienda(tiendaId, request.categoriaIds());
        List<String> skus = skusNormalizados(tiendaId, request.variantes());

        ProductoEntidad producto = new ProductoEntidad();
        producto.setTiendaId(tiendaId);
        producto.setIdentificadorUrl(identificador(tiendaId, request.identificadorUrl(), request.titulo(), null));
        aplicarDatos(producto, request.titulo(), request.descripcion(), request.proveedor(), digital);
        producto.setEstaPublicado(false);
        productos.saveAndFlush(producto);

        asignarCategorias(producto.getId(), categoriasElegidas);
        for (int i = 0; i < request.variantes().size(); i++) {
            VarianteRequest datos = request.variantes().get(i);
            VarianteProductoEntidad variante = VarianteReglas.nueva(producto.getId(), datos, skus.get(i), digital);
            variantes.saveAndFlush(variante);
            inventario.crearInicial(tiendaId, variante.getId(),
                    datos.cantidadInicial() == null ? 0 : datos.cantidadInicial(),
                    datos.rastreaInventario() == null || datos.rastreaInventario(),
                    Boolean.TRUE.equals(datos.permitirVentaSinStock()));
        }
        return detalle(producto);
    }

    /** Datos del producto sin variantes. Marcar como digital quita el envío de sus variantes (CAT-005). */
    @Transactional
    public ProductoDetalleResponse actualizarDatos(ContextoTienda contexto, UUID id, ProductoDatosRequest request) {
        UUID tiendaId = contexto.tiendaId();
        ProductoEntidad producto = buscar(contexto, id);
        if (request.identificadorUrl() != null && !request.identificadorUrl().isBlank()) {
            producto.setIdentificadorUrl(identificador(tiendaId, request.identificadorUrl(), null, id));
        }
        boolean digital = request.esDigital() == null ? Boolean.TRUE.equals(producto.getEsDigital()) : request.esDigital();
        aplicarDatos(producto, request.titulo(), request.descripcion(), request.proveedor(), digital);
        productos.saveAndFlush(producto);
        if (digital) {
            for (VarianteProductoEntidad variante : variantes.findByProductoIdAndEliminadoFalseOrderByFechaCreacionAsc(id)) {
                variante.setRequiereEnvio(false);
                variantes.save(variante);
            }
        }
        return detalle(producto);
    }

    /** CAT-007: publicar exige precio mayor que 0 en alguna variante e imagen principal. */
    @Transactional
    public ProductoDetalleResponse publicar(ContextoTienda contexto, UUID id) {
        ProductoEntidad producto = buscar(contexto, id);
        List<String> faltan = new ArrayList<>();
        boolean conPrecio = variantes.findByProductoIdAndEliminadoFalseOrderByFechaCreacionAsc(id).stream()
                .anyMatch(v -> v.getPrecio().signum() > 0);
        if (!conPrecio) {
            faltan.add("una variante con precio mayor que 0");
        }
        boolean conPrincipal = imagenes.findByProductoIdAndEliminadoFalseOrderByPosicionAscFechaCreacionAsc(id).stream()
                .anyMatch(i -> Boolean.TRUE.equals(i.getEsPrincipal()));
        if (!conPrincipal) {
            faltan.add("una imagen principal");
        }
        if (!faltan.isEmpty()) {
            throw new ReglaNegocioException("CAT-007", "Para publicar falta: " + String.join(" y ", faltan),
                    Map.of("faltan", faltan));
        }
        producto.setEstaPublicado(true);
        productos.saveAndFlush(producto);
        return detalle(producto);
    }

    @Transactional
    public ProductoDetalleResponse despublicar(ContextoTienda contexto, UUID id) {
        ProductoEntidad producto = buscar(contexto, id);
        producto.setEstaPublicado(false);
        productos.saveAndFlush(producto);
        return detalle(producto);
    }

    /** Eliminación lógica del producto y de sus variantes, imágenes y categorías (CAT-008). */
    @Transactional
    public void eliminar(ContextoTienda contexto, UUID id) {
        ProductoEntidad producto = buscar(contexto, id);
        producto.setEliminado(true);
        productos.saveAndFlush(producto);
        for (VarianteProductoEntidad variante : variantes.findByProductoIdAndEliminadoFalseOrderByFechaCreacionAsc(id)) {
            variante.setEliminado(true);
            variantes.save(variante);
        }
        for (ImagenProductoEntidad imagen : imagenes.findByProductoIdAndEliminadoFalseOrderByPosicionAscFechaCreacionAsc(id)) {
            imagen.setEliminado(true);
            imagenes.save(imagen);
        }
        productoCategorias.quitarTodas(id);
    }

    /** CAT-009: reemplaza el conjunto; una categoría de otra tienda responde 404. */
    @Transactional
    public ProductoDetalleResponse reemplazarCategorias(ContextoTienda contexto, UUID id, List<UUID> categoriaIds) {
        ProductoEntidad producto = buscar(contexto, id);
        List<CategoriaEntidad> nuevas = categoriasDeTienda(contexto.tiendaId(), categoriaIds);
        productoCategorias.quitarTodas(id);
        asignarCategorias(id, nuevas);
        return detalle(producto);
    }

    ProductoEntidad buscar(ContextoTienda contexto, UUID id) {
        return productos.findByIdAndTiendaIdAndEliminadoFalse(id, contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("CAT-404", "No encontramos ese producto"));
    }

    private ProductoDetalleResponse detalle(ProductoEntidad producto) {
        List<VarianteProductoEntidad> vs = variantes.findByProductoIdAndEliminadoFalseOrderByFechaCreacionAsc(producto.getId());
        Map<UUID, Cantidades> cantidades = inventario.cantidades(vs.stream().map(VarianteProductoEntidad::getId).toList());
        List<ImagenProductoEntidad> imgs = imagenes.findByProductoIdAndEliminadoFalseOrderByPosicionAscFechaCreacionAsc(producto.getId());
        List<ProductoDetalleResponse.CategoriaRef> cats = categoriasDeProducto(producto.getId());
        return mapeador.detalle(producto, cats, vs, cantidades, imgs);
    }

    private List<ProductoDetalleResponse.CategoriaRef> categoriasDeProducto(UUID productoId) {
        List<UUID> ids = productoCategorias.findByProductoIdAndEliminadoFalse(productoId).stream()
                .map(pc -> pc.getCategoriaId())
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return categorias.findAllById(ids).stream()
                .filter(c -> !Boolean.TRUE.equals(c.getEliminado()))
                .map(mapeador::categoriaRef)
                .toList();
    }

    /** Todas las categorías deben existir en la tienda; si alguna no, 404 (CAT-009). */
    private List<CategoriaEntidad> categoriasDeTienda(UUID tiendaId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> distintas = new HashSet<>(ids);
        List<CategoriaEntidad> encontradas = categorias.findByIdInAndTiendaIdAndEliminadoFalse(distintas, tiendaId);
        if (encontradas.size() != distintas.size()) {
            throw new RecursoNoEncontradoException("CAT-404", "Una de las categorías no existe en tu tienda");
        }
        return encontradas;
    }

    private void asignarCategorias(UUID productoId, List<CategoriaEntidad> categoriasElegidas) {
        for (CategoriaEntidad categoria : categoriasElegidas) {
            ProductoCategoriaEntidad fila = new ProductoCategoriaEntidad();
            fila.setProductoId(productoId);
            fila.setCategoriaId(categoria.getId());
            productoCategorias.saveAndFlush(fila);
        }
    }

    /** Normaliza todos los SKU de la petición y exige que no se repitan entre sí (CAT-003). */
    private List<String> skusNormalizados(UUID tiendaId, List<VarianteRequest> pedidas) {
        List<String> skus = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (VarianteRequest variante : pedidas) {
            String sku = reglas.normalizarSku(tiendaId, variante.sku(), null);
            if (!vistos.add(sku)) {
                throw new ReglaNegocioException("CAT-003", "Dos variantes tienen el mismo SKU: " + sku);
            }
            skus.add(sku);
        }
        return skus;
    }

    private String identificador(UUID tiendaId, String escrito, String titulo, UUID excluirId) {
        if (escrito != null && !escrito.isBlank()) {
            return IdentificadoresUrl.validar(escrito, MAX_IDENTIFICADOR,
                    slug -> excluirId == null
                            ? productos.existsByTiendaIdAndIdentificadorUrlAndEliminadoFalse(tiendaId, slug)
                            : productos.existsByTiendaIdAndIdentificadorUrlAndEliminadoFalseAndIdNot(tiendaId, slug, excluirId));
        }
        return IdentificadoresUrl.generar(titulo == null ? "" : titulo, MAX_IDENTIFICADOR,
                slug -> productos.existsByTiendaIdAndIdentificadorUrlAndEliminadoFalse(tiendaId, slug));
    }

    private static void aplicarDatos(ProductoEntidad producto, String titulo, String descripcion, String proveedor,
                                     boolean digital) {
        producto.setTitulo(titulo.strip());
        producto.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion.strip());
        producto.setProveedor(proveedor == null || proveedor.isBlank() ? null : proveedor.strip());
        producto.setEsDigital(digital);
    }

    private Map<UUID, List<VarianteProductoEntidad>> agrupar(List<ProductoEntidad> lote) {
        Map<UUID, List<VarianteProductoEntidad>> porProducto = new HashMap<>();
        if (lote.isEmpty()) {
            return porProducto;
        }
        List<UUID> ids = lote.stream().map(ProductoEntidad::getId).toList();
        for (VarianteProductoEntidad variante : variantes.findByProductoIdInAndEliminadoFalse(ids)) {
            porProducto.computeIfAbsent(variante.getProductoId(), k -> new ArrayList<>()).add(variante);
        }
        return porProducto;
    }

    private Map<UUID, String> imagenesPrincipales(List<ProductoEntidad> lote) {
        Map<UUID, String> principales = new HashMap<>();
        if (lote.isEmpty()) {
            return principales;
        }
        List<UUID> ids = lote.stream().map(ProductoEntidad::getId).toList();
        for (ImagenProductoEntidad imagen : imagenes.findByProductoIdInAndEliminadoFalseAndEsPrincipalTrue(ids)) {
            principales.put(imagen.getProductoId(), imagen.getUrlImagen());
        }
        return principales;
    }

    private static List<UUID> idsDeVariantes(Map<UUID, List<VarianteProductoEntidad>> porProducto) {
        return porProducto.values().stream()
                .flatMap(Collection::stream)
                .map(VarianteProductoEntidad::getId)
                .toList();
    }

    private static String patron(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String escapado = texto.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escapado + "%";
    }
}
