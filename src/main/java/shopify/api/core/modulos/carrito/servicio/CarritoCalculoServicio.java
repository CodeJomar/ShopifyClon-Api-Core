package shopify.api.core.modulos.carrito.servicio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse.Advertencia;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse.Cupon;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse.Item;
import shopify.api.core.modulos.carrito.entidad.CarritoEntidad;
import shopify.api.core.modulos.carrito.entidad.ItemCarritoEntidad;
import shopify.api.core.modulos.catalogo.modelo.VarianteVendible;
import shopify.api.core.modulos.catalogo.servicio.CatalogoVentaServicio;
import shopify.api.core.modulos.cupon.modelo.CuponAplicable;
import shopify.api.core.modulos.cupon.modelo.EvaluacionCupon;
import shopify.api.core.modulos.cupon.servicio.CuponServicio;
import shopify.api.core.modulos.inventario.modelo.Disponibilidad;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.modulos.tienda.modelo.ConfiguracionVenta;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;
import shopify.api.core.modulos.totales.modelo.CalculadoraTotales;
import shopify.api.core.modulos.totales.modelo.DescuentoCalculo;
import shopify.api.core.modulos.totales.modelo.ImpuestoCalculo;
import shopify.api.core.modulos.totales.modelo.LineaCalculo;
import shopify.api.core.modulos.totales.modelo.TipoDescuentoAplicado;
import shopify.api.core.modulos.totales.modelo.TotalesCalculados;

/**
 * Arma la respuesta del carrito con precios, disponibilidad y cupón actuales (CAR-004, CAR-005, CAR-006).
 * Nunca guarda nada: cada lectura recalcula.
 */
@Service
@RequiredArgsConstructor
public class CarritoCalculoServicio {

    private static final String CAR_006 = "CAR-006";

    private final CatalogoVentaServicio catalogoVenta;
    private final InventarioServicio inventario;
    private final TiendaServicio tiendas;
    private final CuponServicio cupones;

    public CarritoResponse construir(UUID tiendaId, CarritoEntidad carrito, List<ItemCarritoEntidad> items,
                                     List<Advertencia> advertenciasPrevias) {
        ConfiguracionVenta configuracion = tiendas.configuracionVenta(tiendaId);
        List<UUID> ids = items.stream().map(ItemCarritoEntidad::getVarianteId).toList();
        Map<UUID, VarianteVendible> variantes = catalogoVenta.variantesParaVenta(tiendaId, ids);
        Map<UUID, Disponibilidad> disponibilidad = inventario.disponibilidad(ids);

        List<Advertencia> advertencias = new ArrayList<>(advertenciasPrevias);
        List<Item> lineasRespuesta = new ArrayList<>();
        List<LineaCalculo> lineasCalculo = new ArrayList<>();
        BigDecimal subtotalVendible = Dinero.CERO;

        for (ItemCarritoEntidad item : items) {
            UUID varianteId = item.getVarianteId();
            VarianteVendible variante = variantes.get(varianteId);
            Disponibilidad disponible = disponibilidad.get(varianteId);

            if (variante == null || !variante.vendible()) {
                advertencias.add(new Advertencia(CAR_006,
                        "Un producto de tu carrito ya no está disponible y no cuenta en el total", varianteId));
                lineasRespuesta.add(new Item(varianteId, variante == null ? null : variante.productoId(),
                        variante == null ? null : variante.tituloProducto(),
                        variante == null ? null : variante.tituloVariante(),
                        variante == null ? null : variante.urlImagen(),
                        null, item.getCantidad(), Dinero.CERO, false,
                        variante != null && variante.requiereEnvio()));
                continue;
            }

            BigDecimal totalLinea = Dinero.multiplicar(variante.precio(), item.getCantidad());
            lineasCalculo.add(new LineaCalculo(variante.precio(), item.getCantidad(), variante.requiereEnvio()));
            subtotalVendible = subtotalVendible.add(totalLinea);

            boolean disponibleAhora = disponible != null && disponible.disponible();
            if (disponible != null && disponible.rastrea() && !disponible.ventaSinStock()
                    && item.getCantidad() > Math.max(disponible.vendible(), 0)) {
                advertencias.add(new Advertencia("CAR-004",
                        "Pediste " + item.getCantidad() + " y quedan " + Math.max(disponible.vendible(), 0) + " disponibles",
                        varianteId));
            }
            lineasRespuesta.add(new Item(varianteId, variante.productoId(), variante.tituloProducto(),
                    variante.tituloVariante(), variante.urlImagen(), variante.precio(), item.getCantidad(), totalLinea,
                    disponibleAhora, variante.requiereEnvio()));
        }

        Cupon cupon = null;
        DescuentoCalculo descuento = null;
        if (carrito.getCuponId() != null) {
            Optional<String> codigo = cupones.codigoDe(tiendaId, carrito.getCuponId());
            if (codigo.isEmpty()) {
                advertencias.add(new Advertencia("CUP-003", "El cupón ya no existe", null));
            } else if (lineasCalculo.isEmpty()) {
                cupon = new Cupon(codigo.get(), Dinero.CERO);
            } else {
                EvaluacionCupon evaluacion = cupones.evaluarCupon(tiendaId, codigo.get(), subtotalVendible);
                if (evaluacion.aplica()) {
                    CuponAplicable aplicable = evaluacion.aplicable();
                    descuento = new DescuentoCalculo(TipoDescuentoAplicado.valueOf(aplicable.tipo().name()), aplicable.valor());
                } else {
                    advertencias.add(new Advertencia("CUP-003", evaluacion.motivo(), null));
                }
                cupon = new Cupon(codigo.get(), Dinero.CERO);
            }
        }

        if (lineasCalculo.isEmpty()) {
            return new CarritoResponse(carrito.getTokenSesion(), carrito.getEstado().name(), configuracion.moneda(),
                    lineasRespuesta, cupon, Dinero.CERO, Dinero.CERO, Dinero.CERO, Dinero.CERO, false, advertencias);
        }

        TotalesCalculados totales = CalculadoraTotales.calcular(lineasCalculo, descuento, null,
                new ImpuestoCalculo(configuracion.tasaImpuesto(), Boolean.TRUE.equals(configuracion.preciosIncluyenImpuesto())));
        if (cupon != null) {
            cupon = new Cupon(cupon.codigo(), totales.descuentoTotal());
        }
        return new CarritoResponse(carrito.getTokenSesion(), carrito.getEstado().name(), configuracion.moneda(),
                lineasRespuesta, cupon, totales.subtotal(), totales.descuentoTotal(), totales.impuestosTotal(),
                totales.total(), totales.requiereEnvio(), advertencias);
    }
}
