package shopify.api.core.modulos.carrito.repositorio;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.carrito.entidad.CarritoEntidad;
import shopify.api.core.modulos.carrito.modelo.EstadoCarrito;

public interface CarritoRepositorio extends JpaRepository<CarritoEntidad, UUID> {

    /** Token siempre acotado a la tienda: un token de otra tienda no existe (CAR-404). */
    Optional<CarritoEntidad> findFirstByTokenSesionAndTiendaIdAndEliminadoFalse(String tokenSesion, UUID tiendaId);

    /** Un solo ACTIVO por usuario y tienda (UxCarritoActivoUsuario). */
    Optional<CarritoEntidad> findFirstByTiendaIdAndUsuarioIdAndEstadoAndEliminadoFalse(UUID tiendaId, UUID usuarioId,
                                                                                        EstadoCarrito estado);
}
