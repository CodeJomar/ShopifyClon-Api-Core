package shopify.api.core.modulos.balance.servicio;

import java.math.BigDecimal;
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

    /**
     * BAL-001: al confirmar un pago. Ventas += total, comisiones += comisión, órdenes += 1, saldo += total − comisión.
     * Solo lo llama la confirmación de pago (BAL-003); la fila se crea si la tienda aún no tiene balance.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarVenta(UUID tiendaId, BigDecimal total, BigDecimal comision) {
        crearSiNoExiste(tiendaId);
        repositorio.sumarVenta(tiendaId, total, comision, total.subtract(comision));
    }

    /** BAL-002: al reembolsar. Reembolsado += monto y saldo −= monto. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarReembolso(UUID tiendaId, BigDecimal monto) {
        crearSiNoExiste(tiendaId);
        repositorio.sumarReembolso(tiendaId, monto);
    }
}
