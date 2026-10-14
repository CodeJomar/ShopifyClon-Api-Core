package shopify.api.core.modulos.orden.servicio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modulos.orden.entidad.ItemOrdenEntidad;
import shopify.api.core.modulos.orden.entidad.OrdenEntidad;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.ItemOrdenResumen;
import shopify.api.core.modulos.orden.modelo.NuevaLinea;
import shopify.api.core.modulos.orden.modelo.NuevaOrden;
import shopify.api.core.modulos.orden.modelo.OrdenCreada;
import shopify.api.core.modulos.orden.modelo.OrdenResumen;
import shopify.api.core.modulos.orden.repositorio.ItemOrdenRepositorio;
import shopify.api.core.modulos.orden.repositorio.OrdenRepositorio;

/** Dueño de {@code Ordenes} e {@code ItemsOrden}. Solo crea y lee; el checkout decide cuándo y con qué importes. */
@Service
@RequiredArgsConstructor
public class OrdenServicio {

    private final OrdenRepositorio ordenes;
    private final ItemOrdenRepositorio items;

    /** Inserta la orden y sus líneas en la transacción de quien llama; nace ABIERTA / PENDIENTE / NO_PREPARADO. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrdenCreada crear(NuevaOrden datos) {
        OrdenEntidad orden = new OrdenEntidad();
        orden.setTiendaId(datos.tiendaId());
        orden.setUsuarioId(datos.usuarioId());
        orden.setNumeroOrden(datos.numeroOrden());
        orden.setCorreoCliente(datos.correoCliente());
        orden.setNombreCliente(datos.nombreCliente());
        orden.setTelefonoCliente(datos.telefonoCliente());
        orden.setSubtotal(datos.subtotal());
        orden.setDescuentoTotal(datos.descuentoTotal());
        orden.setCostoEnvio(datos.costoEnvio());
        orden.setImpuestosTotal(datos.impuestosTotal());
        orden.setTotal(datos.total());
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setEstadoPago(EstadoPago.PENDIENTE);
        orden.setEstadoPreparacion(EstadoPreparacion.NO_PREPARADO);
        orden.setDireccionEnvio(datos.direccionEnvio());
        orden.setDireccionFacturacion(datos.direccionFacturacion());
        orden.setCuponId(datos.cuponId());
        orden.setCodigoCupon(datos.codigoCupon());
        orden.setNombreTarifaEnvio(datos.nombreTarifaEnvio());
        orden.setPasarelaPago(datos.pasarela());
        orden.setTasaImpuesto(datos.tasaImpuesto());
        orden.setPreciosIncluyenImpuesto(datos.preciosIncluyenImpuesto());
        orden.setClaveIdempotencia(datos.claveIdempotencia());
        orden.setTokenAcceso(datos.tokenAcceso());
        orden.setFechaExpiracionPago(datos.fechaExpiracionPago());
        ordenes.saveAndFlush(orden);

        for (NuevaLinea linea : datos.lineas()) {
            items.saveAndFlush(new ItemOrdenEntidad(orden.getId(), linea.varianteId(), linea.tituloProducto(),
                    linea.tituloVariante(), linea.sku(), linea.cantidad(), linea.precioUnitario(), linea.totalLinea(),
                    linea.requiereEnvio()));
        }
        return new OrdenCreada(orden.getId());
    }

    /** Orden de la tienda con esa clave de idempotencia, con sus líneas. */
    @Transactional(readOnly = true)
    public Optional<OrdenResumen> buscarPorClave(UUID tiendaId, UUID claveIdempotencia) {
        return ordenes.findFirstByTiendaIdAndClaveIdempotenciaAndEliminadoFalse(tiendaId, claveIdempotencia)
                .map(this::resumen);
    }

    private OrdenResumen resumen(OrdenEntidad orden) {
        List<ItemOrdenResumen> lineas = items.findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(orden.getId())
                .stream()
                .map(item -> new ItemOrdenResumen(item.getTituloProducto(), item.getTituloVariante(), item.getSku(),
                        item.getPrecioUnitario(), item.getCantidad(), item.getTotalLinea()))
                .toList();
        return new OrdenResumen(orden.getId(), orden.getNumeroOrden(), orden.getEstado(), orden.getEstadoPago(),
                orden.getEstadoPreparacion(), orden.getSubtotal(), orden.getDescuentoTotal(), orden.getCostoEnvio(),
                orden.getImpuestosTotal(), orden.getTotal(), orden.getPasarelaPago(), orden.getTokenAcceso(),
                orden.getFechaExpiracionPago(), lineas);
    }
}
