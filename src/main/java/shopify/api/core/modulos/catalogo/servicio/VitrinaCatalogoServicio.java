package shopify.api.core.modulos.catalogo.servicio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.modulos.catalogo.dto.response.VitrinaResponses;
import shopify.api.core.modulos.catalogo.entidad.CategoriaEntidad;
import shopify.api.core.modulos.catalogo.entidad.ImagenProductoEntidad;
import shopify.api.core.modulos.catalogo.entidad.ProductoCategoriaEntidad;
import shopify.api.core.modulos.catalogo.entidad.ProductoEntidad;
import shopify.api.core.modulos.catalogo.entidad.VarianteProductoEntidad;
import shopify.api.core.modulos.catalogo.repositorio.CategoriaRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.ImagenProductoRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.ProductoCategoriaRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.ProductoRepositorio;
import shopify.api.core.modulos.catalogo.repositorio.VarianteProductoRepositorio;
import shopify.api.core.modulos.inventario.modelo.Disponibilidad;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/** Catálogo público por subdominio: solo productos publicados de tiendas operativas (CAT-011). */
@Service
@RequiredArgsConstructor
public class VitrinaCatalogoServicio {

    static final int TAMANO_PAGINA = 20;

    private static final Comparator<VarianteProductoEntidad> MAS_BARATA = Comparator
            .comparing(VarianteProductoEntidad::getPrecio)
            .thenComparing(VarianteProductoEntidad::getFechaCreacion);

    private final TiendaServicio tiendas;
    private final CategoriaRepositorio categorias;
    private final ProductoRepositorio productos;
    private final VarianteProductoRepositorio variantes;
    private final ImagenProductoRepositorio imagenes;
    private final ProductoCategoriaRepositorio productoCategorias;
    private final InventarioServicio inventario;

    @Transactional(readOnly = true)
    public List<VitrinaResponses.Categoria> categorias(String subdominio) {
        UUID tiendaId = tienda(subdominio);
        return categorias.conProductosPublicados(tiendaId).stream()
                .map(c -> new VitrinaResponses.Categoria(c.getNombre(), c.getIdentificadorUrl()))
                .toList();
    }

    /** Orden: {@code recientes} (por defecto), {@code precio_asc} o {@code precio_desc}. */
    @Transactional(readOnly = true)
    public Page<VitrinaResponses.ProductoResumen> productos(String subdominio, int pagina, String texto,
                                                           String categoriaSlug, String orden) {
        UUID tiendaId = tienda(subdominio);
        Comparator<VitrinaResponses.ProductoResumen> comparador = comparador(orden);
        List<ProductoEntidad> candidatos = new ArrayList<>(
                productos.buscarPublicados(tiendaId, patron(texto), vacioANulo(categoriaSlug)));
        // Base de "recientes"; el sort de precio es estable y conserva este orden en los empates
        candidatos.sort(Comparator.comparing(ProductoEntidad::getFechaCreacion).reversed());

        Map<UUID, List<VarianteProductoEntidad>> porProducto = variantesPorProducto(candidatos);
        Map<UUID, Disponibilidad> disponibilidad = inventario.disponibilidad(
                porProducto.values().stream().flatMap(List::stream).map(VarianteProductoEntidad::getId).toList());
        Map<UUID, String> principales = imagenesPrincipales(candidatos);

        List<VitrinaResponses.ProductoResumen> resumenes = new ArrayList<>();
        for (ProductoEntidad producto : candidatos) {
            List<VarianteProductoEntidad> vs = porProducto.getOrDefault(producto.getId(), List.of());
            if (vs.isEmpty()) {
                continue;
            }
            VarianteProductoEntidad barata = vs.stream().min(MAS_BARATA).orElseThrow();
            resumenes.add(new VitrinaResponses.ProductoResumen(producto.getTitulo(), producto.getIdentificadorUrl(),
                    barata.getPrecio(), barata.getPrecioComparacion(), principales.get(producto.getId()),
                    disponible(disponibilidad, barata.getId())));
        }
        resumenes.sort(comparador);

        int inicio = Math.min(Math.max(pagina, 0) * TAMANO_PAGINA, resumenes.size());
        int fin = Math.min(inicio + TAMANO_PAGINA, resumenes.size());
        return new PageImpl<>(resumenes.subList(inicio, fin),
                PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA), resumenes.size());
    }

    @Transactional(readOnly = true)
    public VitrinaResponses.ProductoDetalle detalle(String subdominio, String slug) {
        UUID tiendaId = tienda(subdominio);
        String identificador = slug == null ? "" : slug.strip().toLowerCase(Locale.ROOT);
        ProductoEntidad producto = productos.findByTiendaIdAndIdentificadorUrlAndEstaPublicadoTrueAndEliminadoFalse(
                tiendaId, identificador)
                .orElseThrow(() -> new RecursoNoEncontradoException("CAT-404", "No encontramos ese producto"));

        List<VarianteProductoEntidad> vs = variantes.findByProductoIdInAndEliminadoFalse(List.of(producto.getId()))
                .stream()
                .sorted(Comparator.comparing(VarianteProductoEntidad::getFechaCreacion))
                .toList();
        Map<UUID, Disponibilidad> disponibilidad = inventario.disponibilidad(
                vs.stream().map(VarianteProductoEntidad::getId).toList());
        List<ImagenProductoEntidad> imgs = imagenes.findByProductoIdAndEliminadoFalseOrderByPosicionAscFechaCreacionAsc(producto.getId());

        return new VitrinaResponses.ProductoDetalle(
                producto.getTitulo(),
                producto.getIdentificadorUrl(),
                producto.getDescripcion(),
                categoriasDe(producto.getId()),
                vs.stream()
                        .map(v -> new VitrinaResponses.Variante(v.getId(), v.getTitulo(), v.getPrecio(),
                                v.getPrecioComparacion(), disponible(disponibilidad, v.getId()),
                                Boolean.TRUE.equals(v.getRequiereEnvio())))
                        .toList(),
                imgs.stream()
                        .map(i -> new VitrinaResponses.Imagen(i.getUrlImagen(), i.getTextoAlternativo(),
                                i.getVarianteId(), i.getPosicion()))
                        .toList());
    }

    /** TIE-004 y TIE-404: una tienda que no existe o no es operativa responde lo mismo. */
    private UUID tienda(String subdominio) {
        return tiendas.idOperativaPorSubdominio(subdominio)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));
    }

    /** Sin inventario la variante no se vende (no hay forma de saber si queda stock). */
    private static boolean disponible(Map<UUID, Disponibilidad> disponibilidad, UUID varianteId) {
        Disponibilidad d = disponibilidad.get(varianteId);
        return d != null && d.disponible();
    }

    private static Comparator<VitrinaResponses.ProductoResumen> comparador(String orden) {
        String valor = orden == null || orden.isBlank() ? "recientes" : orden.strip().toLowerCase(Locale.ROOT);
        return switch (valor) {
            case "recientes" -> (a, b) -> 0;
            case "precio_asc" -> Comparator.comparing(VitrinaResponses.ProductoResumen::precioMinimo);
            case "precio_desc" -> Comparator.comparing(VitrinaResponses.ProductoResumen::precioMinimo).reversed();
            default -> throw new SolicitudInvalidaException("VAL-003",
                    "El parámetro «orden» debe ser recientes, precio_asc o precio_desc");
        };
    }

    private Map<UUID, List<VarianteProductoEntidad>> variantesPorProducto(List<ProductoEntidad> candidatos) {
        Map<UUID, List<VarianteProductoEntidad>> porProducto = new HashMap<>();
        if (candidatos.isEmpty()) {
            return porProducto;
        }
        for (VarianteProductoEntidad variante : variantes.findByProductoIdInAndEliminadoFalse(
                candidatos.stream().map(ProductoEntidad::getId).toList())) {
            porProducto.computeIfAbsent(variante.getProductoId(), k -> new ArrayList<>()).add(variante);
        }
        return porProducto;
    }

    private Map<UUID, String> imagenesPrincipales(List<ProductoEntidad> candidatos) {
        Map<UUID, String> principales = new HashMap<>();
        if (candidatos.isEmpty()) {
            return principales;
        }
        for (ImagenProductoEntidad imagen : imagenes.findByProductoIdInAndEliminadoFalseAndEsPrincipalTrue(
                candidatos.stream().map(ProductoEntidad::getId).toList())) {
            principales.put(imagen.getProductoId(), imagen.getUrlImagen());
        }
        return principales;
    }

    private List<VitrinaResponses.Categoria> categoriasDe(UUID productoId) {
        List<UUID> ids = productoCategorias.findByProductoIdAndEliminadoFalse(productoId).stream()
                .map(ProductoCategoriaEntidad::getCategoriaId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return categorias.findAllById(ids).stream()
                .filter(c -> !Boolean.TRUE.equals(c.getEliminado()))
                .map(c -> new VitrinaResponses.Categoria(c.getNombre(), c.getIdentificadorUrl()))
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

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip().toLowerCase(Locale.ROOT);
    }
}
