package shopify.api.core.modulos.checkout.servicio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.comun.util.GeneradorToken;
import shopify.api.core.modulos.carrito.modelo.CarritoParaCheckout;
import shopify.api.core.modulos.carrito.modelo.EstadoCarrito;
import shopify.api.core.modulos.carrito.servicio.CarritoCheckoutServicio;
import shopify.api.core.modulos.carrito.servicio.CarritoGlobalServicio;
import shopify.api.core.modulos.checkout.dto.request.CheckoutGlobalRequest;
import shopify.api.core.modulos.checkout.dto.request.CheckoutGlobalRequest.EnvioElegido;
import shopify.api.core.modulos.checkout.modelo.ResultadoCheckoutGlobal;
import shopify.api.core.modulos.checkout.servicio.CheckoutServicio.DatosTienda;
import shopify.api.core.modulos.orden.modelo.NuevoPedido;
import shopify.api.core.modulos.orden.modelo.OrdenResumen;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.orden.modelo.PedidoCreado;
import shopify.api.core.modulos.orden.modelo.PedidoResumen;
import shopify.api.core.modulos.orden.servicio.PedidoConsultaServicio;
import shopify.api.core.modulos.orden.servicio.PedidoServicio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/**
 * Checkout global (R-001, B5): convierte el carrito de varias tiendas en un pedido con una orden hija por tienda,
 * todo en una sola transacción. Cada tienda sigue los pasos de siempre ({@link CheckoutServicio#armarOrden}); si una
 * falla, se deshace todo. Las tiendas se bloquean en orden de id para que dos compras simultáneas no se crucen.
 */
@Service
@RequiredArgsConstructor
public class CheckoutGlobalServicio {

    private static final String UX_PEDIDO = "UxPedidosClaveIdempotencia";
    private static final String UX_ORDEN = "UxOrdenesClaveIdempotencia";

    private final CarritoGlobalServicio carritoGlobal;
    private final CarritoCheckoutServicio carritos;
    private final CheckoutServicio checkout;
    private final PedidoServicio pedidos;
    private final PedidoConsultaServicio consultas;
    private final TiendaServicio tiendas;
    private final GeneradorToken generadorToken;
    private final TransactionTemplate transaccion;

    /** Una compra ya hecha con la misma clave responde el mismo pedido (200); si no, lo crea (201). */
    public ResultadoCheckoutGlobal crear(String token, UUID clave, CheckoutGlobalRequest solicitud) {
        Optional<PedidoResumen> existente = pedidos.buscarPorClave(clave);
        if (existente.isPresent()) {
            return replay(token, existente.get());
        }
        try {
            return transaccion.execute(estado -> crearEnTransaccion(token, clave, solicitud));
        } catch (CarritoYaConvertido ex) {
            // Otra petición con la misma compra ganó el bloqueo del carrito: se responde su pedido
            return pedidos.buscarPorClave(clave).map(pedido -> replay(token, pedido))
                    .orElseThrow(() -> new ConflictoEstadoException("CAR-008",
                            "Este carrito ya se convirtió en una orden. Pide uno nuevo"));
        } catch (DataIntegrityViolationException ex) {
            if (!esClaveDuplicada(ex)) {
                throw ex;
            }
            PedidoResumen ganador = pedidos.buscarPorClave(clave).orElseThrow(() -> ex);
            return replay(token, ganador);
        }
    }

    private ResultadoCheckoutGlobal crearEnTransaccion(String token, UUID clave, CheckoutGlobalRequest solicitud) {
        Map<UUID, String> tokensPorTienda = carritoGlobal.tokensPorTienda(token);
        if (tokensPorTienda.isEmpty()) {
            throw new ConflictoEstadoException("CHK-002", "No hay productos disponibles para comprar");
        }
        Map<UUID, UUID> tarifas = tarifasElegidas(solicitud, tokensPorTienda);
        String moneda = monedaUnica(tokensPorTienda);

        List<OrdenResumen> hijas = new ArrayList<>();
        for (Map.Entry<UUID, String> entrada : tokensPorTienda.entrySet()) {
            UUID tiendaId = entrada.getKey();
            if (!tiendas.esOperativa(tiendaId)) {
                throw new ConflictoEstadoException("CHK-001", "La tienda no está disponible para comprar");
            }
            CarritoParaCheckout carrito = carritos.bloquearParaCheckout(tiendaId, entrada.getValue());
            if (carrito.estado() == EstadoCarrito.CONVERTIDO) {
                throw new CarritoYaConvertido();
            }
            DatosTienda datos = new DatosTienda(solicitud.correoCliente(), solicitud.nombreCliente(),
                    solicitud.telefonoCliente(), solicitud.direccionEnvio(), solicitud.direccionFacturacion(),
                    tarifas.get(tiendaId), solicitud.pasarela());
            hijas.add(checkout.armarOrden(tiendaId, carrito, clave, datos, null));
        }

        BigDecimal subtotal = Dinero.CERO;
        BigDecimal descuento = Dinero.CERO;
        BigDecimal envio = Dinero.CERO;
        BigDecimal impuestos = Dinero.CERO;
        BigDecimal total = Dinero.CERO;
        Instant expiracion = null;
        for (OrdenResumen orden : hijas) {
            subtotal = subtotal.add(orden.subtotal());
            descuento = descuento.add(orden.descuentoTotal());
            envio = envio.add(orden.costoEnvio());
            impuestos = impuestos.add(orden.impuestosTotal());
            total = total.add(orden.total());
            if (expiracion == null || orden.fechaExpiracionPago().isBefore(expiracion)) {
                expiracion = orden.fechaExpiracionPago();
            }
        }
        if (solicitud.totalEsperado().compareTo(total) != 0) {
            throw new ConflictoEstadoException("CHK-004", "El total cambió desde que lo viste. Revísalo antes de confirmar",
                    Map.of("total_recalculado", total));
        }

        PasarelaPago pasarela = solicitud.pasarela();
        PedidoCreado creado = pedidos.crear(new NuevoPedido(SesionActual.usuarioIdOpcional().orElse(null),
                solicitud.correoCliente(), solicitud.nombreCliente(), solicitud.telefonoCliente(), moneda, subtotal,
                descuento, envio, impuestos, total, pasarela, clave, generadorToken.tokenUrl(), expiracion),
                hijas.stream().map(OrdenResumen::id).toList());
        PedidoResumen pedido = pedidos.buscarPorId(creado.id())
                .orElseThrow(() -> new IllegalStateException("El pedido recién creado debía existir"));
        return new ResultadoCheckoutGlobal(consultas.aRespuesta(pedido), true);
    }

    /** Tarifa elegida por tienda; una tienda que no está en el carrito o repetida es VAL-003. */
    private static Map<UUID, UUID> tarifasElegidas(CheckoutGlobalRequest solicitud, Map<UUID, String> delCarrito) {
        Map<UUID, UUID> tarifas = new HashMap<>();
        if (solicitud.envios() == null) {
            return tarifas;
        }
        for (EnvioElegido envio : solicitud.envios()) {
            if (!delCarrito.containsKey(envio.tiendaId()) || tarifas.put(envio.tiendaId(), envio.tarifaEnvioId()) != null) {
                throw new SolicitudInvalidaException("VAL-003", "Elegiste envío para una tienda que no está en tu carrito");
            }
        }
        return tarifas;
    }

    /** CAR-009: todas las tiendas del carrito venden en la misma moneda. */
    private String monedaUnica(Map<UUID, String> tokensPorTienda) {
        String moneda = null;
        for (UUID tiendaId : tokensPorTienda.keySet()) {
            String actual = tiendas.configuracionVenta(tiendaId).moneda();
            if (moneda != null && !moneda.equals(actual)) {
                throw new ConflictoEstadoException("CAR-009", "Tu carrito mezcla monedas (" + moneda + " y " + actual + ")");
            }
            moneda = actual;
        }
        return moneda;
    }

    /** Un pedido existente solo es de este carrito si todas sus órdenes salieron de los carritos del token. */
    private ResultadoCheckoutGlobal replay(String token, PedidoResumen pedido) {
        Set<UUID> ordenesDelCarrito = carritos.ordenesDelToken(token);
        boolean propio = pedido.ordenes().stream().allMatch(orden -> ordenesDelCarrito.contains(orden.ordenId()));
        if (!propio) {
            throw new ConflictoEstadoException("CHK-006", "Esa clave de idempotencia ya se usó con otro carrito");
        }
        return new ResultadoCheckoutGlobal(consultas.aRespuesta(pedido), false);
    }

    private static boolean esClaveDuplicada(DataIntegrityViolationException ex) {
        Throwable causa = ex.getMostSpecificCause();
        String mensaje = causa == null ? null : causa.getMessage();
        return mensaje != null && (mensaje.contains(UX_PEDIDO) || mensaje.contains(UX_ORDEN));
    }

    /** Señal interna: el carrito ya se convirtió mientras esperaba el bloqueo. */
    private static final class CarritoYaConvertido extends RuntimeException {
        CarritoYaConvertido() {
            super("Carrito ya convertido", null, false, false);
        }
    }
}
