package shopify.api.core.modulos.catalogo.servicio;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.catalogo.dto.request.VarianteRequest;
import shopify.api.core.modulos.catalogo.entidad.ProductoEntidad;
import shopify.api.core.modulos.catalogo.entidad.VarianteProductoEntidad;
import shopify.api.core.modulos.catalogo.repositorio.VarianteProductoRepositorio;

/** Reglas de variante: SKU (CAT-003), precio de comparación (CAT-004) y producto digital (CAT-005). */
@Component
@RequiredArgsConstructor
public class VarianteReglas {

    private static final String TITULO_POR_DEFECTO = "Por defecto";

    private final VarianteProductoRepositorio variantes;

    /** SKU en mayúsculas, sin espacios y único entre las variantes no eliminadas de la tienda (D-013). */
    public String normalizarSku(UUID tiendaId, String sku, UUID excluirId) {
        String limpio = sku == null ? "" : sku.strip().toUpperCase(Locale.ROOT);
        if (limpio.isEmpty() || limpio.chars().anyMatch(Character::isWhitespace)) {
            throw new ReglaNegocioException("CAT-003", "El SKU no puede estar vacío ni tener espacios");
        }
        if (variantes.existeSkuEnTienda(tiendaId, limpio, excluirId)) {
            throw new ReglaNegocioException("CAT-003", "Ya usas ese SKU en tu tienda");
        }
        return limpio;
    }

    /** CAT-004: un precio tachado tiene que ser mayor que el precio de venta. */
    public static void exigirComparacionMayor(BigDecimal precio, BigDecimal comparacion) {
        if (comparacion != null && comparacion.compareTo(precio) <= 0) {
            throw new ReglaNegocioException("CAT-004", "El precio de comparación debe ser mayor que el precio");
        }
    }

    /** Nueva variante a partir de la petición. CAT-005: un producto digital no lleva envío. */
    public static VarianteProductoEntidad nueva(UUID productoId, VarianteRequest datos, String sku, boolean digital) {
        exigirComparacionMayor(datos.precio(), datos.precioComparacion());
        VarianteProductoEntidad variante = new VarianteProductoEntidad();
        variante.setProductoId(productoId);
        variante.setTitulo(datos.titulo() == null || datos.titulo().isBlank() ? TITULO_POR_DEFECTO : datos.titulo().strip());
        variante.setSku(sku);
        variante.setCodigoBarras(textoOpcional(datos.codigoBarras()));
        variante.setPrecio(Dinero.redondear(datos.precio()));
        variante.setPrecioComparacion(datos.precioComparacion() == null ? null : Dinero.redondear(datos.precioComparacion()));
        variante.setCostoUnitario(datos.costoUnitario() == null ? null : Dinero.redondear(datos.costoUnitario()));
        variante.setPesoGramos(Dinero.redondear(datos.pesoGramos() == null ? BigDecimal.ZERO : datos.pesoGramos()));
        variante.setRequiereEnvio(!digital && (datos.requiereEnvio() == null || datos.requiereEnvio()));
        return variante;
    }

    /** Aplica los datos de edición a una variante existente (CAT-004, CAT-005). */
    public static void aplicar(VarianteProductoEntidad variante, String titulo, String codigoBarras, BigDecimal precio,
                               BigDecimal precioComparacion, BigDecimal costoUnitario, BigDecimal pesoGramos,
                               Boolean requiereEnvio, String sku, boolean digital) {
        exigirComparacionMayor(precio, precioComparacion);
        variante.setTitulo(titulo == null || titulo.isBlank() ? TITULO_POR_DEFECTO : titulo.strip());
        variante.setSku(sku);
        variante.setCodigoBarras(textoOpcional(codigoBarras));
        variante.setPrecio(Dinero.redondear(precio));
        variante.setPrecioComparacion(precioComparacion == null ? null : Dinero.redondear(precioComparacion));
        variante.setCostoUnitario(costoUnitario == null ? null : Dinero.redondear(costoUnitario));
        variante.setPesoGramos(Dinero.redondear(pesoGramos == null ? BigDecimal.ZERO : pesoGramos));
        variante.setRequiereEnvio(!digital && (requiereEnvio == null || requiereEnvio));
    }

    static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    /** Un producto digital no lleva envío en ninguna de sus variantes (CAT-005). */
    static boolean esDigital(ProductoEntidad producto) {
        return Boolean.TRUE.equals(producto.getEsDigital());
    }
}
