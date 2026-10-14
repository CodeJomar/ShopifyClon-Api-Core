package shopify.api.core.modulos.catalogo.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Proyección de la consulta de venta (alias de la consulta nativa). */
public interface VarianteVentaVista {

    UUID getVarianteId();

    UUID getProductoId();

    String getTituloProducto();

    String getTituloVariante();

    String getSku();

    BigDecimal getPrecio();

    Boolean getRequiereEnvio();

    String getUrlImagen();

    Boolean getVendible();
}
