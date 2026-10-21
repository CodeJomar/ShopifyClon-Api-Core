package shopify.api.core.modulos.carrito.servicio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.comun.util.GeneradorToken;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse;
import shopify.api.core.modulos.carrito.dto.response.CarritoResponse.Advertencia;
import shopify.api.core.modulos.carrito.entidad.CarritoEntidad;
import shopify.api.core.modulos.carrito.entidad.ItemCarritoEntidad;
import shopify.api.core.modulos.carrito.modelo.EstadoCarrito;
import shopify.api.core.modulos.carrito.repositorio.CarritoRepositorio;
import shopify.api.core.modulos.carrito.repositorio.ItemCarritoRepositorio;
import shopify.api.core.modulos.catalogo.modelo.VarianteVendible;
import shopify.api.core.modulos.catalogo.servicio.CatalogoVentaServicio;
import shopify.api.core.modulos.cupon.modelo.CuponAplicable;
import shopify.api.core.modulos.cupon.servicio.CuponServicio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/**
 * Carrito público por subdominio (CAR-001 a CAR-009, D-019). Cada operación devuelve el carrito recalculado.
 */
@Service
@RequiredArgsConstructor
public class CarritoServicio {

    static final int MAX_LINEAS = 50;
    static final int MAX_CANTIDAD = 99;
    private static final int INTENTOS_VINCULAR = 2;

    private final TiendaServicio tiendas;
    private final CarritoRepositorio carritos;
    private final ItemCarritoRepositorio items;
    private final CatalogoVentaServicio catalogoVenta;
    private final CuponServicio cupones;
    private final CarritoCalculoServicio calculo;
    private final GeneradorToken generadorToken;
    private final TransactionTemplate transaccion;

    @Transactional
    public CarritoResponse crear(String subdominio) {
        UUID tiendaId = tienda(subdominio);
        CarritoEntidad carrito = new CarritoEntidad();
        carrito.setTiendaId(tiendaId);
        carrito.setTokenSesion(generadorToken.tokenUrl());
        carrito.setEstado(EstadoCarrito.ACTIVO);
        carritos.saveAndFlush(carrito);
        return calculo.construir(tiendaId, carrito, List.of(), List.of());
    }

    @Transactional(readOnly = true)
    public CarritoResponse obtener(String subdominio, String token) {
        return obtener(subdominio, token, null);
    }

    /** CAR-005 con envío (A-003): con {@code tarifaEnvioId} responde el total exacto que calculará el checkout. */
    @Transactional(readOnly = true)
    public CarritoResponse obtener(String subdominio, String token, UUID tarifaEnvioId) {
        UUID tiendaId = tienda(subdominio);
        CarritoEntidad carrito = buscar(tiendaId, token);
        return calculo.construir(tiendaId, carrito, lineas(carrito), List.of(), tarifaEnvioId);
    }

    /** CAR-002 y CAR-003: variante vendible de la tienda; sumar hasta 99; máximo 50 líneas. */
    @Transactional
    public CarritoResponse agregar(String subdominio, String token, UUID varianteId, int cantidad) {
        UUID tiendaId = tienda(subdominio);
        CarritoEntidad carrito = buscarActivo(tiendaId, token);
        ItemCarritoEntidad existente = itemDe(carrito, varianteId);
        if (existente != null) {
            int nueva = existente.getCantidad() + cantidad;
            if (nueva > MAX_CANTIDAD) {
                throw new ReglaNegocioException("CAR-003", "Máximo " + MAX_CANTIDAD + " unidades por producto");
            }
            existente.setCantidad(nueva);
            items.saveAndFlush(existente);
        } else {
            nuevaLinea(tiendaId, carrito, varianteId, cantidad);
        }
        return calculo.construir(tiendaId, carrito, lineas(carrito), List.of());
    }

    /** Cantidad 0 quita la línea; si no existía, se agrega (con las mismas reglas que {@link #agregar}). */
    @Transactional
    public CarritoResponse fijarCantidad(String subdominio, String token, UUID varianteId, int cantidad) {
        UUID tiendaId = tienda(subdominio);
        CarritoEntidad carrito = buscarActivo(tiendaId, token);
        ItemCarritoEntidad existente = itemDe(carrito, varianteId);
        if (cantidad == 0) {
            if (existente != null) {
                existente.setEliminado(true);
                items.saveAndFlush(existente);
            }
        } else if (existente != null) {
            existente.setCantidad(cantidad);
            items.saveAndFlush(existente);
        } else {
            nuevaLinea(tiendaId, carrito, varianteId, cantidad);
        }
        return calculo.construir(tiendaId, carrito, lineas(carrito), List.of());
    }

    @Transactional
    public CarritoResponse quitar(String subdominio, String token, UUID varianteId) {
        UUID tiendaId = tienda(subdominio);
        CarritoEntidad carrito = buscarActivo(tiendaId, token);
        ItemCarritoEntidad existente = itemDe(carrito, varianteId);
        if (existente != null) {
            existente.setEliminado(true);
            items.saveAndFlush(existente);
        }
        return calculo.construir(tiendaId, carrito, lineas(carrito), List.of());
    }

    /** CUP-005: reemplaza el cupón anterior. Si no aplica, responde 422 CUP-003 con el motivo y no se asocia. */
    @Transactional
    public CarritoResponse aplicarCupon(String subdominio, String token, String codigo) {
        UUID tiendaId = tienda(subdominio);
        CarritoEntidad carrito = buscarActivo(tiendaId, token);
        CarritoResponse actual = calculo.construir(tiendaId, carrito, lineas(carrito), List.of());
        CuponAplicable aplicable = cupones.evaluar(tiendaId, codigo, actual.subtotal());
        carrito.setCuponId(aplicable.id());
        carritos.saveAndFlush(carrito);
        return calculo.construir(tiendaId, carrito, lineas(carrito), List.of());
    }

    @Transactional
    public CarritoResponse quitarCupon(String subdominio, String token) {
        UUID tiendaId = tienda(subdominio);
        CarritoEntidad carrito = buscarActivo(tiendaId, token);
        carrito.setCuponId(null);
        carritos.saveAndFlush(carrito);
        return calculo.construir(tiendaId, carrito, lineas(carrito), List.of());
    }

    /**
     * D-019: sin carrito activo del usuario en la tienda, el del token pasa a ser suyo; si ya tiene uno, el del
     * token se fusiona en el suyo y queda CONVERTIDO. Una carrera con UxCarritoActivoUsuario reintenta como fusión.
     */
    public CarritoResponse vincular(String subdominio, String token) {
        UUID usuarioId = SesionActual.usuarioId();
        UUID tiendaId = tienda(subdominio);
        for (int intento = 1; ; intento++) {
            try {
                return transaccion.execute(estado -> vincularEnTransaccion(tiendaId, token, usuarioId));
            } catch (DataIntegrityViolationException ex) {
                if (intento >= INTENTOS_VINCULAR) {
                    throw ex;
                }
            }
        }
    }

    private CarritoResponse vincularEnTransaccion(UUID tiendaId, String token, UUID usuarioId) {
        CarritoEntidad origen = buscar(tiendaId, token);
        exigirActivo(origen);
        if (usuarioId.equals(origen.getUsuarioId())) {
            return calculo.construir(tiendaId, origen, lineas(origen), List.of());
        }

        Optional<CarritoEntidad> destino = carritos.findFirstByTiendaIdAndUsuarioIdAndEstadoAndEliminadoFalse(
                tiendaId, usuarioId, EstadoCarrito.ACTIVO);
        if (destino.isEmpty()) {
            origen.setUsuarioId(usuarioId);
            carritos.saveAndFlush(origen);
            return calculo.construir(tiendaId, origen, lineas(origen), List.of());
        }

        CarritoEntidad carritoDelUsuario = destino.get();
        List<Advertencia> advertencias = fusionar(carritoDelUsuario, origen);
        origen.setEstado(EstadoCarrito.CONVERTIDO);
        carritos.saveAndFlush(origen);
        return calculo.construir(tiendaId, carritoDelUsuario, lineas(carritoDelUsuario), advertencias);
    }

    /** Suma las líneas del invitado en las del usuario (tope 99); sobre 50 líneas se descartan con advertencia. */
    private List<Advertencia> fusionar(CarritoEntidad destino, CarritoEntidad origen) {
        List<Advertencia> advertencias = new ArrayList<>();
        Map<UUID, ItemCarritoEntidad> porVariante = new HashMap<>();
        List<ItemCarritoEntidad> actuales = lineas(destino);
        actuales.forEach(item -> porVariante.put(item.getVarianteId(), item));

        for (ItemCarritoEntidad linea : lineas(origen)) {
            ItemCarritoEntidad existente = porVariante.get(linea.getVarianteId());
            if (existente != null) {
                existente.setCantidad(Math.min(MAX_CANTIDAD, existente.getCantidad() + linea.getCantidad()));
                items.saveAndFlush(existente);
            } else if (porVariante.size() < MAX_LINEAS) {
                ItemCarritoEntidad nueva = nuevaEntidad(destino.getId(), linea.getVarianteId(), linea.getCantidad());
                items.saveAndFlush(nueva);
                porVariante.put(linea.getVarianteId(), nueva);
            } else {
                advertencias.add(new Advertencia("CAR-003",
                        "Se descartó un producto de tu carrito anterior: el carrito admite " + MAX_LINEAS + " productos distintos",
                        linea.getVarianteId()));
            }
        }
        if (destino.getCuponId() == null) {
            destino.setCuponId(origen.getCuponId());
            carritos.saveAndFlush(destino);
        }
        return advertencias;
    }

    /** CAR-002 y CAR-003 para una línea nueva. */
    private void nuevaLinea(UUID tiendaId, CarritoEntidad carrito, UUID varianteId, int cantidad) {
        exigirVendible(tiendaId, varianteId);
        if (lineas(carrito).size() >= MAX_LINEAS) {
            throw new ReglaNegocioException("CAR-003", "Un carrito admite hasta " + MAX_LINEAS + " productos distintos");
        }
        items.saveAndFlush(nuevaEntidad(carrito.getId(), varianteId, cantidad));
    }

    private void exigirVendible(UUID tiendaId, UUID varianteId) {
        VarianteVendible variante = catalogoVenta.variantesParaVenta(tiendaId, List.of(varianteId)).get(varianteId);
        if (variante == null || !variante.vendible()) {
            throw new ReglaNegocioException("CAR-002", "Ese producto no está disponible para la compra");
        }
    }

    private static ItemCarritoEntidad nuevaEntidad(UUID carritoId, UUID varianteId, int cantidad) {
        ItemCarritoEntidad item = new ItemCarritoEntidad();
        item.setCarritoId(carritoId);
        item.setVarianteId(varianteId);
        item.setCantidad(cantidad);
        return item;
    }

    private UUID tienda(String subdominio) {
        return tiendas.idOperativaPorSubdominio(subdominio)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));
    }

    /** CAR-001: el token es de esta tienda; si no existe o no es de ella, 404 CAR-404. */
    private CarritoEntidad buscar(UUID tiendaId, String token) {
        return carritos.findFirstByTokenSesionAndTiendaIdAndEliminadoFalse(token == null ? "" : token, tiendaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("CAR-404", "No encontramos ese carrito"));
    }

    private CarritoEntidad buscarActivo(UUID tiendaId, String token) {
        CarritoEntidad carrito = buscar(tiendaId, token);
        exigirActivo(carrito);
        return carrito;
    }

    /** CAR-008: un carrito convertido no se modifica. */
    private static void exigirActivo(CarritoEntidad carrito) {
        if (carrito.getEstado() == EstadoCarrito.CONVERTIDO) {
            throw new ConflictoEstadoException("CAR-008", "Este carrito ya se convirtió en una orden. Pide uno nuevo");
        }
    }

    private ItemCarritoEntidad itemDe(CarritoEntidad carrito, UUID varianteId) {
        return items.findFirstByCarritoIdAndVarianteIdAndEliminadoFalse(carrito.getId(), varianteId).orElse(null);
    }

    private List<ItemCarritoEntidad> lineas(CarritoEntidad carrito) {
        if (carrito.getId() == null) {
            return List.of();
        }
        return new ArrayList<>(items.findByCarritoIdAndEliminadoFalseOrderByFechaCreacionAsc(carrito.getId()));
    }
}
