package shopify.api.core.modulos.catalogo.servicio;

import java.math.BigDecimal;
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
import shopify.api.core.comun.campana.PrecioCampana;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.modulos.campana.servicio.CampanaServicio;
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
import shopify.api.core.comun.util.DescripcionCorta;
import shopify.api.core.modulos.resena.dto.response.ResenaResponses.Calificacion;
import shopify.api.core.modulos.resena.servicio.ResenaServicio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/** Catálogo público por subdominio: solo productos publicados de tiendas operativas (CAT-011). */
@Service
@RequiredArgsConstructor
public class VitrinaCatalogoServicio {

    static final int TAMANO_PAGINA = 20;

    private static final Comparator<VarianteProductoEntidad> MAS_BARATA = Comparator
            .comparing(VarianteProductoEntidad::getPrecio)
            .thenComparing(VarianteProductoEntidad::getFechaCreacion);

    private final CampanaServicio campanas;
    private final TiendaServicio tiendas;
    private final CategoriaRepositorio categorias;
    private final ProductoRepositorio productos;
    private final VarianteProductoRepositorio variantes;
    private final ImagenProductoRepositorio imagenes;
    private final ProductoCategoriaRepositorio productoCategorias;
    private final InventarioServicio inventario;
    private final ResenaServicio resenas;

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
        Map<UUID, Calificacion> calificaciones = resenas.calificaciones(candidatos.stream().map(ProductoEntidad::getId).toList());

        List<VitrinaResponses.ProductoResumen> resumenes = new ArrayList<>();
        for (ProductoEntidad producto : candidatos) {
            List<VarianteProductoEntidad> vs = porProducto.getOrDefault(producto.getId(), List.of());
            if (vs.isEmpty()) {
                continue;
            }
            VarianteProductoEntidad barata = vs.stream().min(MAS_BARATA).orElseThrow();
            PrecioCampana precio = campanas.aplicar(barata.getPrecio());
            resumenes.add(new VitrinaResponses.ProductoResumen(producto.getTitulo(), producto.getIdentificadorUrl(),
                    precio.precioFinal(), comparacion(precio, barata.getPrecioComparacion()), principales.get(producto.getId()),
                    disponible(disponibilidad, barata.getId()),
                    calificaciones.containsKey(producto.getId()) ? calificaciones.get(producto.getId()).promedio() : null,
                    calificaciones.containsKey(producto.getId()) ? calificaciones.get(producto.getId()).cantidad() : 0L,
                    DescripcionCorta.de(producto.getDescripcion()), resumenVariantes(vs), vs.size(),
                    precio.hayCampana() ? precio.porcentaje() : null, precio.campana()));
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
        Calificacion calificacion = resenas.calificaciones(List.of(producto.getId())).get(producto.getId());

        return new VitrinaResponses.ProductoDetalle(
                producto.getTitulo(),
                producto.getIdentificadorUrl(),
                producto.getDescripcion(),
                categoriasDe(producto.getId()),
                vs.stream()
                        .map(v -> {
                            PrecioCampana precio = campanas.aplicar(v.getPrecio());
                            return new VitrinaResponses.Variante(v.getId(), v.getTitulo(), precio.precioFinal(),
                                    comparacion(precio, v.getPrecioComparacion()), disponible(disponibilidad, v.getId()),
                                    Boolean.TRUE.equals(v.getRequiereEnvio()),
                                    precio.hayCampana() ? precio.porcentaje() : null, precio.campana());
                        })
                        .toList(),
                imgs.stream()
                        .map(i -> new VitrinaResponses.Imagen(i.getUrlImagen(), i.getTextoAlternativo(),
                                i.getVarianteId(), i.getPosicion()))
                        .toList(),
                calificacion == null ? null : calificacion.promedio(),
                calificacion == null ? 0L : calificacion.cantidad());
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
            case "mejor_calificados" -> Comparator
                    .comparing((VitrinaResponses.ProductoResumen p) -> p.calificacionPromedio() == null
                            ? BigDecimal.ZERO : p.calificacionPromedio()).reversed()
                    .thenComparing(Comparator.comparingLong(VitrinaResponses.ProductoResumen::cantidadResenas).reversed());
            default -> throw new SolicitudInvalidaException("VAL-003",
                    "El parámetro «orden» debe ser recientes, precio_asc, precio_desc o mejor_calificados");
        };
    }

    /** Hasta tres variantes en el orden en que se definieron; una sola «por defecto» no es una opción que mostrar. */
    private List<VitrinaResponses.VarianteResumen> resumenVariantes(List<VarianteProductoEntidad> variantes) {
        List<VarianteProductoEntidad> ordenadas = variantes.stream()
                .sorted(Comparator.comparing(VarianteProductoEntidad::getFechaCreacion)).toList();
        if (ordenadas.size() == 1 && List.of("por defecto", "predeterminada")
                .contains(ordenadas.get(0).getTitulo().strip().toLowerCase(Locale.ROOT))) {
            return List.of();
        }
        return ordenadas.stream().limit(3).map(v -> {
            PrecioCampana precio = campanas.aplicar(v.getPrecio());
            return new VitrinaResponses.VarianteResumen(v.getTitulo(), precio.precioFinal(),
                    precio.hayCampana() ? precio.precioLista() : null);
        }).toList();
    }

    /** Con campaña la comparación es el precio de lista; sin campaña queda la que ya tenía la variante. */
    private static java.math.BigDecimal comparacion(PrecioCampana precio, java.math.BigDecimal propia) {
        return precio.hayCampana() ? precio.precioLista() : propia;
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
