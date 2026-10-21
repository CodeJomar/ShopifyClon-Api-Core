package shopify.api.core.modulos.checkout.servicio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.comun.util.GeneradorToken;
import shopify.api.core.modulos.carrito.modelo.CarritoParaCheckout;
import shopify.api.core.modulos.carrito.modelo.EstadoCarrito;
import shopify.api.core.modulos.carrito.modelo.LineaCarrito;
import shopify.api.core.modulos.carrito.servicio.CarritoCalculoServicio;
import shopify.api.core.modulos.carrito.servicio.CarritoCheckoutServicio;
import shopify.api.core.modulos.catalogo.modelo.VarianteVendible;
import shopify.api.core.modulos.catalogo.servicio.CatalogoVentaServicio;
import shopify.api.core.modulos.checkout.dto.request.CheckoutRequest;
import shopify.api.core.modulos.checkout.dto.request.DireccionCheckoutRequest;
import shopify.api.core.modulos.checkout.dto.response.CheckoutResponse;
import shopify.api.core.modulos.checkout.dto.response.CheckoutResponse.ItemCheckoutResponse;
import shopify.api.core.modulos.checkout.modelo.ResultadoCheckout;
import shopify.api.core.modulos.cupon.modelo.CuponAplicable;
import shopify.api.core.modulos.cupon.modelo.EvaluacionCupon;
import shopify.api.core.modulos.cupon.servicio.CuponServicio;
import shopify.api.core.modulos.envio.modelo.TarifaAplicable;
import shopify.api.core.modulos.envio.servicio.TarifaEnvioServicio;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.modulos.orden.eventos.OrdenCreadaEvento;
import shopify.api.core.modulos.orden.modelo.NuevaLinea;
import shopify.api.core.modulos.orden.modelo.NuevaOrden;
import shopify.api.core.modulos.orden.modelo.OrdenCreada;
import shopify.api.core.modulos.orden.modelo.OrdenResumen;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.modulos.tienda.modelo.ConfiguracionVenta;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;
import shopify.api.core.modulos.totales.modelo.DescuentoCalculo;
import shopify.api.core.modulos.totales.modelo.LineaCalculo;
import shopify.api.core.modulos.totales.modelo.TotalesCalculados;

/**
 * Convierte un carrito en una orden en los 13 pasos de {@code docs/negocio/flujos/checkout.md}. El orden de bloqueo
 * es contrato: carrito (paso 3) → tienda (paso 8) → inventario (paso 10, dentro de {@code reservar}) → cupón (paso 11).
 * No se lee inventario dentro de la transacción antes del paso 10.
 */
@Service
@RequiredArgsConstructor
public class CheckoutServicio {

    static final Duration PLAZO_PAGO_STRIPE = Duration.ofMinutes(30);
    static final Duration PLAZO_PAGO_MANUAL = Duration.ofHours(48);
    private static final String UX_CLAVE_IDEMPOTENCIA = "UxOrdenesClaveIdempotencia";

    private final TiendaServicio tiendas;
    private final CarritoCheckoutServicio carritos;
    private final CatalogoVentaServicio catalogoVenta;
    private final TarifaEnvioServicio tarifas;
    private final CuponServicio cupones;
    private final CarritoCalculoServicio calculoCarrito;
    private final InventarioServicio inventario;
    private final OrdenServicio ordenes;
    private final GeneradorToken generadorToken;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    /**
     * Pasos 1 a 13. Antes de la transacción (paso 1) busca la orden por clave; si ya existe, responde sin tocar nada.
     * Dentro de la transacción (pasos 2 a 12) cualquier error deshace todo, incluido el número de orden.
     */
    public ResultadoCheckout crear(String subdominio, String token, UUID clave, CheckoutRequest solicitud) {
        UUID tiendaId = tiendas.idPorSubdominio(subdominio)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));

        Optional<OrdenResumen> existente = ordenes.buscarPorClave(tiendaId, clave);
        if (existente.isPresent()) {
            return replay(tiendaId, token, existente.get());
        }

        try {
            return transaccion.execute(estado -> crearEnTransaccion(tiendaId, token, clave, solicitud));
        } catch (DataIntegrityViolationException ex) {
            // Paso 13: otra petición con la misma clave ganó la carrera; se relee su orden fuera de la transacción.
            if (!esClaveDuplicada(ex)) {
                throw ex;
            }
            OrdenResumen ganadora = ordenes.buscarPorClave(tiendaId, clave).orElseThrow(() -> ex);
            return replay(tiendaId, token, ganadora);
        }
    }

    private ResultadoCheckout crearEnTransaccion(UUID tiendaId, String token, UUID clave, CheckoutRequest solicitud) {
        // Paso 2: la tienda debe estar operativa (CHK-001)
        if (!tiendas.esOperativa(tiendaId)) {
            throw new ConflictoEstadoException("CHK-001", "La tienda no está disponible para comprar");
        }

        // Paso 3: bloquea el carrito. Si ya se convirtió, puede ser una repetición concurrente de esta misma compra.
        CarritoParaCheckout carrito = carritos.bloquearParaCheckout(tiendaId, token);
        if (carrito.estado() == EstadoCarrito.CONVERTIDO) {
            return repeticionConcurrente(tiendaId, carrito, clave);
        }

        // Paso 4: solo líneas vendibles, con precios actuales (CHK-002 si no queda ninguna)
        if (carrito.lineas().isEmpty()) {
            throw new ConflictoEstadoException("CHK-002", "No hay productos disponibles para comprar");
        }
        List<NuevaLinea> lineas = new ArrayList<>();
        List<LineaCalculo> lineasCalculo = new ArrayList<>();
        BigDecimal subtotal = Dinero.CERO;
        boolean requiereEnvio = false;
        Map<UUID, VarianteVendible> variantes = catalogoVenta.variantesParaVenta(tiendaId, idsDe(carrito));
        for (LineaCarrito linea : carrito.lineas()) {
            VarianteVendible variante = variantes.get(linea.varianteId());
            if (variante == null || !variante.vendible()) {
                continue;
            }
            BigDecimal totalLinea = Dinero.multiplicar(variante.precio(), linea.cantidad());
            lineas.add(new NuevaLinea(variante.varianteId(), variante.tituloProducto(), variante.tituloVariante(),
                    variante.sku(), linea.cantidad(), variante.precio(), totalLinea, variante.requiereEnvio()));
            lineasCalculo.add(new LineaCalculo(variante.precio(), linea.cantidad(), variante.requiereEnvio()));
            subtotal = subtotal.add(totalLinea);
            requiereEnvio = requiereEnvio || variante.requiereEnvio();
        }
        if (lineas.isEmpty()) {
            throw new ConflictoEstadoException("CHK-002", "No hay productos disponibles para comprar");
        }

        // Paso 5: con envío, dirección obligatoria (VAL-001) y tarifa activa de la tienda (ENV-001)
        TarifaAplicable tarifa = null;
        if (requiereEnvio) {
            if (solicitud.direccionEnvio() == null) {
                throw new ReglaNegocioException("VAL-001", "Indica la dirección de envío");
            }
            if (solicitud.tarifaEnvioId() == null) {
                throw new ConflictoEstadoException("ENV-001", "Elige un método de envío");
            }
            tarifa = tarifas.buscarActiva(tiendaId, solicitud.tarifaEnvioId());
        }

        // Paso 6: el cupón del carrito debe seguir aplicando (CHK-005)
        UUID cuponId = null;
        String codigoCupon = null;
        DescuentoCalculo descuento = null;
        if (carrito.cuponId() != null) {
            String codigo = cupones.codigoDe(tiendaId, carrito.cuponId())
                    .orElseThrow(() -> new ConflictoEstadoException("CHK-005", "El cupón ya no existe"));
            EvaluacionCupon evaluacion = cupones.evaluarCupon(tiendaId, codigo, subtotal);
            if (!evaluacion.aplica()) {
                throw new ConflictoEstadoException("CHK-005", evaluacion.motivo());
            }
            CuponAplicable aplicable = evaluacion.aplicable();
            cuponId = aplicable.id();
            codigoCupon = aplicable.codigo();
            descuento = CarritoCalculoServicio.descuentoDe(aplicable);
        }

        // Paso 7: recalcular y comparar con lo que vio el comprador (CHK-004)
        ConfiguracionVenta configuracion = tiendas.configuracionVenta(tiendaId);
        boolean incluido = Boolean.TRUE.equals(configuracion.preciosIncluyenImpuesto());
        // Misma función que usa la cotización del carrito (A-003): el total estimado es el que se compara aquí
        TotalesCalculados totales = calculoCarrito.calcularTotales(lineasCalculo, descuento, tarifa, configuracion);
        if (solicitud.totalEsperado().compareTo(totales.total()) != 0) {
            throw new ConflictoEstadoException("CHK-004", "El total cambió desde que lo viste. Revísalo antes de confirmar",
                    Map.of("total_recalculado", totales.total()));
        }

        // Paso 8: número correlativo; bloquea la fila de la tienda hasta el commit
        String numeroOrden = tiendas.tomarNumeroOrden(tiendaId);

        // Paso 9: la orden y sus líneas, con instantáneas. Nace ABIERTA / PENDIENTE / NO_PREPARADO.
        String tokenAcceso = generadorToken.tokenUrl();
        Instant expiracion = reloj.instant().plus(
                solicitud.pasarela() == PasarelaPago.STRIPE ? PLAZO_PAGO_STRIPE : PLAZO_PAGO_MANUAL);
        OrdenCreada creada = ordenes.crear(new NuevaOrden(tiendaId, SesionActual.usuarioIdOpcional().orElse(null),
                numeroOrden, solicitud.correoCliente(), solicitud.nombreCliente(), solicitud.telefonoCliente(),
                totales.subtotal(), totales.descuentoTotal(), totales.costoEnvio(), totales.impuestosTotal(),
                totales.total(), cuponId, codigoCupon, tarifa == null ? null : tarifa.nombre(), solicitud.pasarela(),
                configuracion.tasaImpuesto(), incluido, clave, tokenAcceso, expiracion,
                direccion(solicitud.direccionEnvio()),
                direccion(solicitud.direccionFacturacion() != null
                        ? solicitud.direccionFacturacion() : solicitud.direccionEnvio()),
                lineas));

        // Paso 10: reservar stock con el id de la orden (INV-002, todo o nada). Es la primera lectura de stock.
        inventario.reservar(tiendaId, lineas.stream()
                .map(linea -> new LineaInventario(linea.varianteId(), linea.cantidad())).toList(), creada.id());

        // Paso 11: consumir un uso del cupón (CHK-005 si ya no queda ninguno)
        if (cuponId != null) {
            cupones.consumirUso(cuponId);
        }

        // Paso 12: el carrito queda CONVERTIDO y ligado a la orden; el evento se publica dentro de la transacción
        carritos.marcarConvertido(carrito.carritoId(), creada.id());
        eventos.publishEvent(new OrdenCreadaEvento(tiendaId, creada.id(), numeroOrden));

        OrdenResumen orden = ordenes.buscarPorClave(tiendaId, clave)
                .orElseThrow(() -> new IllegalStateException("La orden recién creada debía existir"));
        return new ResultadoCheckout(respuesta(orden, configuracion.moneda()), true);
    }

    /**
     * Misma compra repetida mientras la primera seguía en curso: el carrito ya se convirtió con esta orden. Responde
     * la misma orden. Un carrito convertido con otra orden, o con esta clave pero no esta orden, es CAR-008.
     */
    private ResultadoCheckout repeticionConcurrente(UUID tiendaId, CarritoParaCheckout carrito, UUID clave) {
        Optional<OrdenResumen> propia = ordenes.buscarPorClave(tiendaId, clave)
                .filter(orden -> orden.id().equals(carrito.ordenId()));
        if (propia.isEmpty()) {
            throw new ConflictoEstadoException("CAR-008", "Este carrito ya se convirtió en una orden. Pide uno nuevo");
        }
        return new ResultadoCheckout(respuesta(propia.get(), tiendas.configuracionVenta(tiendaId).moneda()), false);
    }

    /** Paso 1 o paso 13: la clave ya tiene orden. Solo es de este carrito si el carrito la lleva ligada. */
    private ResultadoCheckout replay(UUID tiendaId, String token, OrdenResumen orden) {
        Optional<UUID> ordenDelCarrito = carritos.ordenDe(tiendaId, token);
        if (ordenDelCarrito.isEmpty() || !ordenDelCarrito.get().equals(orden.id())) {
            throw new ConflictoEstadoException("CHK-006", "Esa clave de idempotencia ya se usó con otro carrito");
        }
        return new ResultadoCheckout(respuesta(orden, tiendas.configuracionVenta(tiendaId).moneda()), false);
    }

    private static boolean esClaveDuplicada(DataIntegrityViolationException ex) {
        Throwable causa = ex.getMostSpecificCause();
        return causa != null && causa.getMessage() != null && causa.getMessage().contains(UX_CLAVE_IDEMPOTENCIA);
    }

    private static List<UUID> idsDe(CarritoParaCheckout carrito) {
        return carrito.lineas().stream().map(LineaCarrito::varianteId).toList();
    }

    private static CheckoutResponse respuesta(OrdenResumen orden, String moneda) {
        List<ItemCheckoutResponse> items = orden.items().stream()
                .map(item -> new ItemCheckoutResponse(item.tituloProducto(), item.tituloVariante(), item.sku(),
                        item.precioUnitario(), item.cantidad(), item.totalLinea()))
                .toList();
        return new CheckoutResponse(orden.id(), orden.numeroOrden(), orden.estado(), orden.estadoPago(), moneda, items,
                orden.subtotal(), orden.descuentoTotal(), orden.costoEnvio(), orden.impuestosTotal(), orden.total(),
                orden.pasarela(), orden.tokenAcceso(), orden.fechaExpiracionPago());
    }

    /** Snapshot de la dirección en las claves de la API (snake_case), tal como se guarda en JSONB. */
    private static Map<String, Object> direccion(DireccionCheckoutRequest direccion) {
        if (direccion == null) {
            return null;
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("nombre_contacto", direccion.nombreContacto());
        mapa.put("telefono", direccion.telefono());
        mapa.put("direccion_linea1", direccion.direccionLinea1());
        mapa.put("direccion_linea2", direccion.direccionLinea2());
        mapa.put("ciudad", direccion.ciudad());
        mapa.put("estado_provincia", direccion.estadoProvincia());
        mapa.put("codigo_postal", direccion.codigoPostal());
        mapa.put("pais", direccion.pais());
        return mapa;
    }
}
