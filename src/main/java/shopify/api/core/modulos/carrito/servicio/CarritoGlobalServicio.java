package shopify.api.core.modulos.carrito.servicio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.comun.util.GeneradorToken;
import shopify.api.core.modulos.carrito.dto.request.CotizarCarritoRequest.EnvioElegido;
import shopify.api.core.modulos.carrito.dto.response.CarritoGlobalResponse;
import shopify.api.core.modulos.carrito.dto.response.CarritoGlobalResponse.TarifasDeTienda;
import shopify.api.core.modulos.carrito.dto.response.CarritoGlobalResponse.TiendaCarrito;
import shopify.api.core.modulos.carrito.dto.response.CarritoGlobalResponse.TiendaDelCarrito;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse;
import shopify.api.core.modulos.carrito.entidad.CarritoEntidad;
import shopify.api.core.modulos.carrito.entidad.ItemCarritoEntidad;
import shopify.api.core.modulos.carrito.modelo.EstadoCarrito;
import shopify.api.core.modulos.carrito.repositorio.CarritoRepositorio;
import shopify.api.core.modulos.catalogo.servicio.CatalogoVentaServicio;
import shopify.api.core.modulos.envio.servicio.TarifaEnvioServicio;
import shopify.api.core.modulos.tienda.modelo.TiendaPublica;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/**
 * Carrito global (R-001, B4): un carrito por navegador ({@code X-Carrito-Token}) o por persona con líneas de varias
 * tiendas. Por dentro sigue siendo un carrito por tienda (las mismas filas y reglas de {@link CarritoServicio}), todos
 * con el mismo token; esta clase los agrupa, valida la moneda única (CAR-009) y suma el total general.
 */
@Service
@RequiredArgsConstructor
public class CarritoGlobalServicio {

    private final CarritoRepositorio carritos;
    private final CarritoServicio carritoServicio;
    private final CarritoCalculoServicio calculo;
    private final CatalogoVentaServicio catalogoVenta;
    private final TiendaServicio tiendas;
    private final TarifaEnvioServicio tarifas;
    private final GeneradorToken generadorToken;

    /** Carrito vacío con un token nuevo (no escribe nada: el primer producto crea las filas). */
    public CarritoGlobalResponse crear() {
        return new CarritoGlobalResponse(generadorToken.tokenUrl(), null, 0, List.of(), Dinero.CERO);
    }

    @Transactional(readOnly = true)
    public CarritoGlobalResponse ver(String token) {
        return construir(token, activos(token), Map.of());
    }

    @Transactional(readOnly = true)
    public CarritoGlobalResponse cotizar(String token, List<EnvioElegido> envios) {
        List<CarritoEntidad> activos = activos(token);
        Map<UUID, UUID> porTienda = new LinkedHashMap<>();
        for (EnvioElegido envio : envios) {
            if (activos.stream().noneMatch(c -> c.getTiendaId().equals(envio.tiendaId()))) {
                throw new SolicitudInvalidaException("VAL-003", "Elegiste envío para una tienda que no está en tu carrito");
            }
            porTienda.put(envio.tiendaId(), envio.tarifaEnvioId());
        }
        return construir(token, activos, porTienda);
    }

    @Transactional(readOnly = true)
    public List<TarifasDeTienda> tarifas(String token) {
        List<TarifasDeTienda> resultado = new ArrayList<>();
        for (CarritoEntidad carrito : activos(token)) {
            if (carritoServicio.lineasDe(carrito).isEmpty()) {
                continue;
            }
            tiendas.publicaOperativa(carrito.getTiendaId()).ifPresent(tienda -> resultado.add(
                    new TarifasDeTienda(aTienda(tienda), tarifas.listarActivas(tienda.id()))));
        }
        return resultado;
    }

    /** Agrega una variante (de cualquier tienda operativa). Devuelve el carrito y, en el token, el de la persona. */
    @Transactional
    public CarritoGlobalResponse agregar(String token, UUID varianteId, int cantidad) {
        String efectivo = tokenODeNuevo(token);
        UUID tiendaId = tiendaDe(varianteId);
        exigirMismaMoneda(efectivo, tiendaId);
        carritoServicio.agregarEn(tiendaId, carritoDeEscritura(efectivo, tiendaId, true), varianteId, cantidad);
        return ver(efectivo);
    }

    /** Cantidad 0 quita la línea. */
    @Transactional
    public CarritoGlobalResponse fijarCantidad(String token, UUID varianteId, int cantidad) {
        String efectivo = tokenODeNuevo(token);
        UUID tiendaId = tiendaDe(varianteId);
        if (cantidad > 0) {
            exigirMismaMoneda(efectivo, tiendaId);
        }
        CarritoEntidad carrito = carritoDeEscritura(efectivo, tiendaId, cantidad > 0);
        if (carrito != null) {
            carritoServicio.fijarCantidadEn(tiendaId, carrito, varianteId, cantidad);
        }
        return ver(efectivo);
    }

    @Transactional
    public CarritoGlobalResponse aplicarCupon(String token, UUID tiendaId, String codigo) {
        CarritoEntidad carrito = carritoDeEscritura(token, tiendaId, false);
        if (carrito == null) {
            throw new RecursoNoEncontradoException("CAR-404", "No encontramos ese carrito");
        }
        carritoServicio.fijarCuponEn(tiendaId, carrito, codigo);
        return ver(token);
    }

    @Transactional
    public CarritoGlobalResponse quitarCupon(String token, UUID tiendaId) {
        CarritoEntidad carrito = carritoDeEscritura(token, tiendaId, false);
        if (carrito != null) {
            carritoServicio.fijarCuponEn(tiendaId, carrito, null);
        }
        return ver(token);
    }

    /** Requiere sesión: cada carrito por tienda del token pasa a la persona (o se fusiona con el que ya tenía). */
    public CarritoGlobalResponse vincular(String token) {
        SesionActual.usuarioId();
        for (CarritoEntidad carrito : delToken(token)) {
            carritoServicio.vincularTienda(carrito.getTiendaId(), token);
        }
        return ver(token);
    }

    /**
     * Para el checkout global: por cada tienda con líneas (ordenadas por id, orden de bloqueo estable), el token real
     * del carrito de esa tienda (puede diferir del pedido si la persona vinculó otro carrito).
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> tokensPorTienda(String token) {
        Map<UUID, String> resultado = new java.util.TreeMap<>();
        for (CarritoEntidad carrito : activos(token)) {
            if (!carritoServicio.lineasDe(carrito).isEmpty()) {
                resultado.put(carrito.getTiendaId(), carrito.getTokenSesion());
            }
        }
        return resultado;
    }

    // ------------------------------------------------------------------ internos

    private String tokenODeNuevo(String token) {
        return token == null || token.isBlank() ? generadorToken.tokenUrl() : token;
    }

    /** Un carrito activo por tienda: el de la persona si lo tiene, si no el del token. */
    private List<CarritoEntidad> activos(String token) {
        UUID usuarioId = SesionActual.usuarioIdOpcional().orElse(null);
        Map<UUID, CarritoEntidad> porTienda = new LinkedHashMap<>();
        List<CarritoEntidad> candidatos = new ArrayList<>(delToken(token));
        if (usuarioId != null) {
            candidatos.addAll(carritos.findByUsuarioIdAndEstadoAndEliminadoFalseOrderByFechaCreacionAsc(usuarioId,
                    EstadoCarrito.ACTIVO));
        }
        for (CarritoEntidad carrito : candidatos) {
            CarritoEntidad previo = porTienda.get(carrito.getTiendaId());
            if (previo == null || (usuarioId != null && usuarioId.equals(carrito.getUsuarioId()))) {
                porTienda.put(carrito.getTiendaId(), carrito);
            }
        }
        return new ArrayList<>(porTienda.values());
    }

    private List<CarritoEntidad> delToken(String token) {
        return carritos.findByTokenSesionAndEstadoAndEliminadoFalseOrderByFechaCreacionAsc(
                token == null ? "" : token, EstadoCarrito.ACTIVO);
    }

    private CarritoEntidad carritoDeEscritura(String token, UUID tiendaId, boolean crear) {
        for (CarritoEntidad carrito : activos(token)) {
            if (carrito.getTiendaId().equals(tiendaId)) {
                return carrito;
            }
        }
        if (!crear) {
            return null;
        }
        if (carritos.findFirstByTokenSesionAndTiendaIdAndEliminadoFalse(token, tiendaId).isPresent()) {
            throw new ConflictoEstadoException("CAR-008", "Este carrito ya se convirtió en una orden. Pide uno nuevo");
        }
        CarritoEntidad nuevo = new CarritoEntidad();
        nuevo.setTiendaId(tiendaId);
        nuevo.setTokenSesion(token);
        nuevo.setEstado(EstadoCarrito.ACTIVO);
        return carritos.saveAndFlush(nuevo);
    }

    private UUID tiendaDe(UUID varianteId) {
        UUID tiendaId = catalogoVenta.tiendaDeVariante(varianteId)
                .orElseThrow(() -> new ReglaNegocioException("CAR-002", "Ese producto no está disponible para la compra"));
        if (tiendas.publicaOperativa(tiendaId).isEmpty()) {
            throw new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible");
        }
        return tiendaId;
    }

    /** CAR-009: un carrito tiene una sola moneda; una tienda con otra moneda no se puede mezclar. */
    private void exigirMismaMoneda(String token, UUID tiendaId) {
        String moneda = tiendas.publicaOperativa(tiendaId).map(TiendaPublica::moneda).orElse(null);
        for (CarritoEntidad carrito : activos(token)) {
            if (carrito.getTiendaId().equals(tiendaId) || carritoServicio.lineasDe(carrito).isEmpty()) {
                continue;
            }
            String actual = tiendas.publicaOperativa(carrito.getTiendaId()).map(TiendaPublica::moneda).orElse(null);
            if (actual != null && moneda != null && !actual.equals(moneda)) {
                throw new ConflictoEstadoException("CAR-009",
                        "Tu carrito está en " + actual + "; esta tienda vende en " + moneda);
            }
        }
    }

    private CarritoGlobalResponse construir(String token, List<CarritoEntidad> activos, Map<UUID, UUID> envios) {
        List<TiendaCarrito> grupos = new ArrayList<>();
        BigDecimal total = Dinero.CERO;
        int articulos = 0;
        String moneda = null;
        for (CarritoEntidad carrito : activos) {
            List<ItemCarritoEntidad> lineas = carritoServicio.lineasDe(carrito);
            if (lineas.isEmpty()) {
                continue;
            }
            TiendaPublica tienda = tiendas.publicaOperativa(carrito.getTiendaId()).orElse(null);
            if (tienda == null) {
                continue;
            }
            CarritoResponse r = calculo.construir(tienda.id(), carrito, lineas, List.of(), envios.get(tienda.id()));
            grupos.add(new TiendaCarrito(aTienda(tienda), r.moneda(), r.items(), r.cupon(), r.subtotal(),
                    r.descuentoTotal(), r.costoEnvio(), r.impuestosTotal(), r.totalEstimado(), r.requiereEnvio(),
                    r.advertencias()));
            total = total.add(r.totalEstimado());
            articulos += lineas.stream().mapToInt(ItemCarritoEntidad::getCantidad).sum();
            moneda = moneda == null ? r.moneda() : moneda;
        }
        return new CarritoGlobalResponse(token, moneda, articulos, grupos, total);
    }

    private static TiendaDelCarrito aTienda(TiendaPublica tienda) {
        return new TiendaDelCarrito(tienda.id(), tienda.nombre(), tienda.subdominio());
    }
}
