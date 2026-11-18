package shopify.api.core.modulos.mercado.servicio;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.CategoriaMercadoResponse;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.DestacadosMercadoResponse;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.ProductoMercadoResponse;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.ResumenMercado;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.TiendaMercadoDetalleResponse;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.TiendaMercadoResponse;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.TiendaResumen;
import shopify.api.core.modulos.mercado.modelo.CategoriaMercado;
import shopify.api.core.modulos.mercado.modelo.FiltroProductosMercado;
import shopify.api.core.modulos.mercado.modelo.FiltroProductosMercado.OrdenProductos;
import shopify.api.core.modulos.mercado.modelo.ProductoMercado;
import shopify.api.core.modulos.mercado.modelo.TiendaMercado;
import shopify.api.core.modulos.mercado.repositorio.MercadoRepositorio;

/** Mercado público (R-001): productos y tiendas de todas las tiendas visibles, sin sesión. */
@Service
@RequiredArgsConstructor
public class MercadoServicio {

    static final int TAMANO_POR_DEFECTO = 24;
    static final int TAMANO_MAXIMO = 48;
    private static final int LARGO_MAXIMO_TEXTO = 100;
    private static final int TIENDAS_DESTACADAS = 6;
    private static final int PRODUCTOS_DESTACADOS = 12;

    private final MercadoRepositorio repositorio;

    @Transactional(readOnly = true)
    public Page<ProductoMercadoResponse> productos(String texto, String categoria, String tienda, BigDecimal precioMin,
                                                   BigDecimal precioMax, String orden, Integer pagina, Integer tamano) {
        int numero = pagina == null ? 0 : pagina;
        int cuantos = tamano == null ? TAMANO_POR_DEFECTO : tamano;
        if (numero < 0 || cuantos < 1 || cuantos > TAMANO_MAXIMO) {
            throw new SolicitudInvalidaException("VAL-003", "La página debe ser 0 o más y el tamaño de 1 a " + TAMANO_MAXIMO);
        }
        if (precioMin != null && precioMax != null && precioMin.compareTo(precioMax) > 0) {
            throw new SolicitudInvalidaException("VAL-003", "El precio mínimo no puede ser mayor que el máximo");
        }
        FiltroProductosMercado filtro = new FiltroProductosMercado(
                patronDeBusqueda(texto), vacioANulo(categoria), vacioANulo(tienda), precioMin, precioMax,
                ordenDe(orden), cuantos, numero * cuantos);
        List<ProductoMercadoResponse> filas = repositorio.productos(filtro).stream().map(MercadoServicio::aProducto).toList();
        return new PageImpl<>(filas, PageRequest.of(numero, cuantos), repositorio.contarProductos(filtro));
    }

    @Transactional(readOnly = true)
    public Page<TiendaMercadoResponse> tiendas(String texto, Integer pagina, Integer tamano) {
        int numero = pagina == null ? 0 : pagina;
        int cuantos = tamano == null ? TAMANO_POR_DEFECTO : tamano;
        if (numero < 0 || cuantos < 1 || cuantos > TAMANO_MAXIMO) {
            throw new SolicitudInvalidaException("VAL-003", "La página debe ser 0 o más y el tamaño de 1 a " + TAMANO_MAXIMO);
        }
        String patron = patronDeBusqueda(texto);
        List<TiendaMercadoResponse> filas = repositorio.tiendas(patron, false, cuantos, numero * cuantos).stream()
                .map(MercadoServicio::aTienda).toList();
        return new PageImpl<>(filas, PageRequest.of(numero, cuantos), repositorio.contarTiendas(patron));
    }

    /** TIE-404: una tienda que no existe, está oculta o no es operativa responde igual. */
    @Transactional(readOnly = true)
    public TiendaMercadoDetalleResponse tienda(String subdominio) {
        TiendaMercado tienda = repositorio.tienda(subdominio == null ? "" : subdominio.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));
        List<CategoriaMercadoResponse> categorias = repositorio.categorias(tienda.subdominio()).stream()
                .map(MercadoServicio::aCategoria).toList();
        return new TiendaMercadoDetalleResponse(tienda.nombre(), tienda.subdominio(), tienda.descripcion(), tienda.logoUrl(),
                tienda.moneda(), tienda.cantidadProductos(), categorias);
    }

    @Transactional(readOnly = true)
    public List<CategoriaMercadoResponse> categorias() {
        return repositorio.categorias(null).stream().map(MercadoServicio::aCategoria).toList();
    }

    /** Para la landing: las tiendas más recientes y los productos más recientes, más el total de cada cosa. */
    @Transactional(readOnly = true)
    public DestacadosMercadoResponse destacados() {
        List<TiendaMercadoResponse> tiendas = repositorio.tiendas(null, true, TIENDAS_DESTACADAS, 0).stream()
                .map(MercadoServicio::aTienda).toList();
        FiltroProductosMercado recientes = new FiltroProductosMercado(null, null, null, null, null,
                OrdenProductos.RECIENTES, PRODUCTOS_DESTACADOS, 0);
        List<ProductoMercadoResponse> productos = repositorio.productos(recientes).stream().map(MercadoServicio::aProducto).toList();
        return new DestacadosMercadoResponse(tiendas, productos,
                new ResumenMercado(repositorio.contarTiendas(null), repositorio.contarProductosVisibles()));
    }

    // ------------------------------------------------------------------ utilidades

    private static OrdenProductos ordenDe(String orden) {
        if (orden == null || orden.isBlank() || orden.equalsIgnoreCase("recientes")) {
            return OrdenProductos.RECIENTES;
        }
        return switch (orden.strip().toLowerCase(Locale.ROOT)) {
            case "precio_asc" -> OrdenProductos.PRECIO_ASC;
            case "precio_desc" -> OrdenProductos.PRECIO_DESC;
            case "mejor_calificados" -> OrdenProductos.MEJOR_CALIFICADOS;
            default -> throw new SolicitudInvalidaException("VAL-003",
                    "El orden debe ser recientes, precio_asc, precio_desc o mejor_calificados");
        };
    }

    /** Minúsculas, sin tildes y con los comodines de LIKE escapados; nulo si no hay texto. */
    static String patronDeBusqueda(String texto) {
        String limpio = vacioANulo(texto);
        if (limpio == null) {
            return null;
        }
        if (limpio.length() > LARGO_MAXIMO_TEXTO) {
            throw new SolicitudInvalidaException("VAL-003", "La búsqueda no puede superar " + LARGO_MAXIMO_TEXTO + " caracteres");
        }
        String sinTildes = Normalizer.normalize(limpio, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String minusculas = sinTildes.toLowerCase(Locale.ROOT);
        return minusculas.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private static ProductoMercadoResponse aProducto(ProductoMercado p) {
        return new ProductoMercadoResponse(p.id(), p.titulo(), p.identificadorUrl(), p.precioDesde(), p.precioComparacionDesde(),
                p.moneda(), p.imagenUrl(), p.esDigital(), p.hayStock(),
                new TiendaResumen(p.tiendaNombre(), p.tiendaSubdominio(), p.tiendaLogoUrl()),
                p.calificacionPromedio(), p.cantidadResenas());
    }

    private static TiendaMercadoResponse aTienda(TiendaMercado t) {
        return new TiendaMercadoResponse(t.nombre(), t.subdominio(), t.descripcion(), t.logoUrl(), t.moneda(), t.cantidadProductos());
    }

    private static CategoriaMercadoResponse aCategoria(CategoriaMercado c) {
        return new CategoriaMercadoResponse(c.nombre(), c.slug(), c.cantidadProductos());
    }
}
