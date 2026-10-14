package shopify.api.core.modulos.carrito.repositorio;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.carrito.entidad.CarritoEntidad;
import shopify.api.core.modulos.carrito.modelo.EstadoCarrito;

public interface CarritoRepositorio extends JpaRepository<CarritoEntidad, UUID> {

    /** Token siempre acotado a la tienda: un token de otra tienda no existe (CAR-404). */
    Optional<CarritoEntidad> findFirstByTokenSesionAndTiendaIdAndEliminadoFalse(String tokenSesion, UUID tiendaId);

    /** Un solo ACTIVO por usuario y tienda (UxCarritoActivoUsuario). */
    Optional<CarritoEntidad> findFirstByTiendaIdAndUsuarioIdAndEstadoAndEliminadoFalse(UUID tiendaId, UUID usuarioId,
                                                                                        EstadoCarrito estado);

    /**
     * Paso 3 del checkout: bloquea el carrito (FOR UPDATE). Dos checkouts del mismo carrito se serializan aquí, y el
     * segundo ve el carrito ya CONVERTIDO.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CarritoEntidad c WHERE c.tokenSesion = :token AND c.tiendaId = :tiendaId "
            + "AND c.eliminado = FALSE")
    Optional<CarritoEntidad> bloquearPorToken(@Param("token") String token, @Param("tiendaId") UUID tiendaId);
}
