package shopify.api.core.modulos.catalogo.servicio;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modulos.catalogo.modelo.VarianteVendible;
import shopify.api.core.modulos.catalogo.modelo.VarianteVentaVista;
import shopify.api.core.modulos.catalogo.repositorio.VarianteVentaRepositorio;

/** Precios y disponibilidad de venta de las variantes de una tienda (carrito y checkout). */
@Service
@RequiredArgsConstructor
public class CatalogoVentaServicio {

    private final VarianteVentaRepositorio variantes;

    /**
     * Mapa por id de variante. Una variante que no existe en la tienda no aparece; una que existe pero no se
     * puede vender sale con {@code vendible = false}.
     */
    @Transactional(readOnly = true)
    public Map<UUID, VarianteVendible> variantesParaVenta(UUID tiendaId, Collection<UUID> varianteIds) {
        Map<UUID, VarianteVendible> resultado = new HashMap<>();
        if (varianteIds == null || varianteIds.isEmpty()) {
            return resultado;
        }
        for (VarianteVentaVista fila : variantes.buscarParaVenta(tiendaId, varianteIds)) {
            resultado.put(fila.getVarianteId(), new VarianteVendible(fila.getVarianteId(), fila.getProductoId(),
                    fila.getTituloProducto(), fila.getTituloVariante(), fila.getSku(), fila.getPrecio(),
                    Boolean.TRUE.equals(fila.getRequiereEnvio()), fila.getUrlImagen(),
                    Boolean.TRUE.equals(fila.getVendible())));
        }
        return resultado;
    }
}
