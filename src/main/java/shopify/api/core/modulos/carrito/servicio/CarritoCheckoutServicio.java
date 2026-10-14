package shopify.api.core.modulos.carrito.servicio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.carrito.entidad.CarritoEntidad;
import shopify.api.core.modulos.carrito.modelo.CarritoParaCheckout;
import shopify.api.core.modulos.carrito.modelo.EstadoCarrito;
import shopify.api.core.modulos.carrito.modelo.LineaCarrito;
import shopify.api.core.modulos.carrito.repositorio.CarritoRepositorio;
import shopify.api.core.modulos.carrito.repositorio.ItemCarritoRepositorio;

/**
 * Operaciones del carrito que necesita el checkout: leer la orden ya ligada, bloquear el carrito y marcarlo como
 * convertido. Las reglas del checkout (estado, replay, errores) viven en el módulo checkout.
 */
@Service
@RequiredArgsConstructor
public class CarritoCheckoutServicio {

    private final CarritoRepositorio carritos;
    private final ItemCarritoRepositorio items;

    /** Paso 1 del checkout: la orden que ya tiene el carrito, si la tiene. CAR-404 si el token no es de la tienda. */
    @Transactional(readOnly = true)
    public Optional<UUID> ordenDe(UUID tiendaId, String token) {
        return Optional.ofNullable(buscar(tiendaId, token).getOrdenId());
    }

    /**
     * Paso 3 del checkout: bloquea el carrito (FOR UPDATE) y lo devuelve con sus líneas. No decide el estado: eso lo
     * hace quien llama. Requiere transacción abierta, porque el bloqueo dura hasta su commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CarritoParaCheckout bloquearParaCheckout(UUID tiendaId, String token) {
        CarritoEntidad carrito = carritos.bloquearPorToken(token == null ? "" : token, tiendaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("CAR-404", "No encontramos ese carrito"));
        List<LineaCarrito> lineas = items.findByCarritoIdAndEliminadoFalseOrderByFechaCreacionAsc(carrito.getId())
                .stream()
                .map(item -> new LineaCarrito(item.getVarianteId(), item.getCantidad()))
                .toList();
        return new CarritoParaCheckout(carrito.getId(), carrito.getEstado(), carrito.getCuponId(), carrito.getOrdenId(),
                lineas);
    }

    /** Paso 12 del checkout: el carrito queda CONVERTIDO y ligado a su orden. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void marcarConvertido(UUID carritoId, UUID ordenId) {
        CarritoEntidad carrito = carritos.findById(carritoId)
                .orElseThrow(() -> new IllegalStateException("El carrito " + carritoId + " debía estar bloqueado"));
        carrito.setEstado(EstadoCarrito.CONVERTIDO);
        carrito.setOrdenId(ordenId);
        carritos.saveAndFlush(carrito);
    }

    private CarritoEntidad buscar(UUID tiendaId, String token) {
        return carritos.findFirstByTokenSesionAndTiendaIdAndEliminadoFalse(token == null ? "" : token, tiendaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("CAR-404", "No encontramos ese carrito"));
    }
}
