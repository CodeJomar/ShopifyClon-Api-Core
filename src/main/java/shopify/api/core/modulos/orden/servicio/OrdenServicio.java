package shopify.api.core.modulos.orden.servicio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.orden.entidad.ItemOrdenEntidad;
import shopify.api.core.modulos.orden.entidad.OrdenEntidad;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.ItemOrdenResumen;
import shopify.api.core.modulos.orden.modelo.LineaOrden;
import shopify.api.core.modulos.orden.modelo.NuevaLinea;
import shopify.api.core.modulos.orden.modelo.NuevaOrden;
import shopify.api.core.modulos.orden.modelo.OrdenCreada;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.modelo.OrdenResumen;
import shopify.api.core.modulos.orden.repositorio.ItemOrdenRepositorio;
import shopify.api.core.modulos.orden.repositorio.OrdenRepositorio;

/**
 * Dueño de {@code Ordenes} e {@code ItemsOrden}. Crea, lee y aplica las transiciones de estado. Cada transición
 * bloquea la orden (FOR UPDATE) y valida con los mapas de transición de sus enums; un cambio no permitido es 409.
 */
@Service
@RequiredArgsConstructor
public class OrdenServicio {

    private final OrdenRepositorio ordenes;
    private final ItemOrdenRepositorio items;
    private final Clock reloj;

    /** Inserta la orden y sus líneas en la transacción de quien llama; nace ABIERTA / PENDIENTE / NO_PREPARADO. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrdenCreada crear(NuevaOrden datos) {
        OrdenEntidad orden = new OrdenEntidad();
        orden.setTiendaId(datos.tiendaId());
        orden.setUsuarioId(datos.usuarioId());
        orden.setNumeroOrden(datos.numeroOrden());
        orden.setCorreoCliente(datos.correoCliente().trim().toLowerCase(Locale.ROOT));
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

    /** ORD-404 si la orden no existe o es de otra tienda. Para lecturas del panel. */
    @Transactional(readOnly = true)
    public void exigirDeTienda(UUID tiendaId, UUID ordenId) {
        ordenes.findFirstByIdAndTiendaIdAndEliminadoFalse(ordenId, tiendaId).orElseThrow(OrdenServicio::noEncontrada);
    }

    /** Bloquea la orden de la tienda (FOR UPDATE) y la devuelve con lo necesario para decidir y operar. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrdenEstado bloquear(UUID tiendaId, UUID ordenId) {
        OrdenEntidad orden = ordenes.bloquearPorIdYTienda(ordenId, tiendaId).orElseThrow(OrdenServicio::noEncontrada);
        List<LineaOrden> lineas = lineasDe(orden.getId());
        return new OrdenEstado(orden.getId(), orden.getEstado(), orden.getEstadoPago(), orden.getEstadoPreparacion(),
                orden.getTotal(), orden.getPasarelaPago(), orden.getFechaExpiracionPago(), orden.getCuponId(), lineas,
                lineas.stream().anyMatch(LineaOrden::requiereEnvio));
    }

    /**
     * PAG-005: pago aprobado. Pago PENDIENTE → PAGADO con su comisión. Si ninguna línea requiere envío, la orden
     * queda ENTREGADO y CERRADA (flujo digital). Solo desde una orden ABIERTA con pago PENDIENTE (ORD-005).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void aplicarPagoConfirmado(UUID ordenId, BigDecimal comision) {
        OrdenEntidad orden = bloqueada(ordenId);
        if (orden.getEstadoPago() != EstadoPago.PENDIENTE || orden.getEstado() != EstadoOrden.ABIERTA) {
            throw new ConflictoEstadoException("ORD-005", "La orden ya no admite un pago confirmado");
        }
        orden.setEstadoPago(EstadoPago.PAGADO);
        orden.setComisionPlataforma(comision);
        if (lineasDe(ordenId).stream().noneMatch(LineaOrden::requiereEnvio)) {
            // Flujo digital: no hay envío que preparar; la orden se entrega y se cierra en el mismo pago (ORD-004)
            orden.setEstadoPreparacion(EstadoPreparacion.ENTREGADO);
            cerrar(orden);
        }
        ordenes.saveAndFlush(orden);
    }

    /** ORD-007 sin pago: pago PENDIENTE → ANULADO y la orden CANCELADA. Solo si NO_PREPARADO y ABIERTA. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void anular(UUID ordenId, String motivo, String detalle) {
        OrdenEntidad orden = bloqueada(ordenId);
        if (orden.getEstadoPago() != EstadoPago.PENDIENTE || orden.getEstado() != EstadoOrden.ABIERTA
                || orden.getEstadoPreparacion() != EstadoPreparacion.NO_PREPARADO) {
            throw new ConflictoEstadoException("ORD-007", "Solo se anula una orden sin pagar y sin preparar");
        }
        exigirMotivo(motivo);
        orden.setEstadoPago(EstadoPago.ANULADO);
        cancelar(orden, motivo, detalle);
        ordenes.saveAndFlush(orden);
    }

    /** ORD-006: NO_PREPARADO → PREPARADO, solo con pago PAGADO y orden ABIERTA. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void marcarPreparada(UUID ordenId) {
        OrdenEntidad orden = bloqueada(ordenId);
        if (orden.getEstado() != EstadoOrden.ABIERTA || orden.getEstadoPago() != EstadoPago.PAGADO) {
            throw new ConflictoEstadoException("ORD-006", "Solo se prepara una orden pagada");
        }
        avanzarPreparacion(orden, EstadoPreparacion.PREPARADO);
        ordenes.saveAndFlush(orden);
    }

    /** ORD-006 y ORD-004: PREPARADO → ENTREGADO; si el pago está cobrado, la orden se cierra. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void marcarEntregada(UUID ordenId) {
        OrdenEntidad orden = bloqueada(ordenId);
        if (orden.getEstado() != EstadoOrden.ABIERTA) {
            throw new ConflictoEstadoException("ORD-006", "La orden no está abierta");
        }
        avanzarPreparacion(orden, EstadoPreparacion.ENTREGADO);
        if (orden.getEstadoPago().estaCobrado()) {
            cerrar(orden);
        }
        ordenes.saveAndFlush(orden);
    }

    /** PAG-007/008: reembolso total (REEMBOLSADO) o parcial (PARCIALMENTE_REEMBOLSADO) según la suma acumulada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void aplicarReembolso(UUID ordenId, boolean total) {
        OrdenEntidad orden = bloqueada(ordenId);
        EstadoPago destino = total ? EstadoPago.REEMBOLSADO : EstadoPago.PARCIALMENTE_REEMBOLSADO;
        if (!orden.getEstadoPago().puedeTransicionarA(destino)) {
            throw new ConflictoEstadoException("ORD-005", "El pago no admite ese reembolso");
        }
        orden.setEstadoPago(destino);
        ordenes.saveAndFlush(orden);
    }

    /**
     * ORD-007 pagada: la orden queda CANCELADA. Solo si ABIERTA, NO_PREPARADO y con dinero cobrado. El estado de pago
     * lo fija {@link #aplicarReembolso}, no esta operación.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelarPagada(UUID ordenId, String motivo, String detalle) {
        OrdenEntidad orden = bloqueada(ordenId);
        if (orden.getEstado() != EstadoOrden.ABIERTA || orden.getEstadoPreparacion() != EstadoPreparacion.NO_PREPARADO
                || !orden.getEstadoPago().estaCobrado()) {
            throw new ConflictoEstadoException("ORD-007", "Solo se cancela una orden pagada sin preparar");
        }
        exigirMotivo(motivo);
        cancelar(orden, motivo, detalle);
        ordenes.saveAndFlush(orden);
    }

    private OrdenEntidad bloqueada(UUID ordenId) {
        return ordenes.bloquearPorId(ordenId).orElseThrow(OrdenServicio::noEncontrada);
    }

    private void avanzarPreparacion(OrdenEntidad orden, EstadoPreparacion destino) {
        if (!orden.getEstadoPreparacion().puedeTransicionarA(destino)) {
            throw new ConflictoEstadoException("ORD-006", "La orden no puede pasar a " + destino.name().toLowerCase(Locale.ROOT));
        }
        orden.setEstadoPreparacion(destino);
    }

    /** ORD-004: ABIERTA → CERRADA con fecha de cierre. */
    private void cerrar(OrdenEntidad orden) {
        if (!orden.getEstado().puedeTransicionarA(EstadoOrden.CERRADA)) {
            throw new ConflictoEstadoException("ORD-005", "La orden no se puede cerrar");
        }
        orden.setEstado(EstadoOrden.CERRADA);
        orden.setFechaCierre(Instant.now(reloj));
    }

    private void cancelar(OrdenEntidad orden, String motivo, String detalle) {
        if (!orden.getEstado().puedeTransicionarA(EstadoOrden.CANCELADA)) {
            throw new ConflictoEstadoException("ORD-007", "La orden no se puede cancelar");
        }
        orden.setEstado(EstadoOrden.CANCELADA);
        orden.setMotivoCancelacion(motivo);
        orden.setDetalleCancelacion(detalle);
        orden.setFechaCancelacion(Instant.now(reloj));
    }

    private static void exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("VAL-001", "Indica el motivo de la cancelación");
        }
    }

    private List<LineaOrden> lineasDe(UUID ordenId) {
        return items.findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(ordenId).stream()
                .map(item -> new LineaOrden(item.getVarianteId(), item.getCantidad(), item.getRequiereEnvio()))
                .toList();
    }

    private static RecursoNoEncontradoException noEncontrada() {
        return new RecursoNoEncontradoException("ORD-404", "No encontramos esa orden");
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
