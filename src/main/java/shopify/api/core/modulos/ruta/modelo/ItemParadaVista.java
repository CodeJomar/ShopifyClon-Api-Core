package shopify.api.core.modulos.ruta.modelo;

import java.util.UUID;

/** Línea de una orden que viaja en una parada. */
public interface ItemParadaVista {

    UUID getOrdenId();

    String getTituloProducto();

    String getTituloVariante();

    String getSku();

    Integer getCantidad();
}
