package shopify.api.core.modulos.balance.servicio;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modulos.balance.entidad.BalanceTiendaEntidad;
import shopify.api.core.modulos.balance.repositorio.BalanceTiendaRepositorio;

/** Balance acumulado de cada tienda. Servicio interno: lo usa el alta de tienda (T-005). */
@Service
@RequiredArgsConstructor
public class BalanceServicio {

    private final BalanceTiendaRepositorio repositorio;

    /** Crea el balance en cero si la tienda aún no tiene uno; si ya existe, no hace nada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void crearSiNoExiste(UUID tiendaId) {
        if (repositorio.existsByTiendaId(tiendaId)) {
            return;
        }
        BalanceTiendaEntidad balance = new BalanceTiendaEntidad();
        balance.setTiendaId(tiendaId);
        repositorio.save(balance);
    }
}
