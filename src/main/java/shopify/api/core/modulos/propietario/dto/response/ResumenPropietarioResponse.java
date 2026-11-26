package shopify.api.core.modulos.propietario.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Panel general del propietario (R-001, B7): todas sus tiendas en una vista. Las monedas distintas no se suman:
 * los totales y la serie van por moneda.
 */
public record ResumenPropietarioResponse(
        LocalDate desde,
        LocalDate hasta,
        List<TotalMoneda> totales,
        List<PuntoDia> serieDia,
        List<TiendaResumen> tiendas) {

    public record TotalMoneda(String moneda, BigDecimal ventas, int ordenes, BigDecimal saldoPendiente) {
    }

    public record PuntoDia(String moneda, LocalDate fecha, BigDecimal ventas, int ordenes) {
    }

    public record Alertas(long ordenesPorPreparar, long stockBajo) {
    }

    public record TiendaResumen(
            UUID tiendaId,
            String nombre,
            String subdominio,
            String ruc,
            String moneda,
            String estadoSuscripcion,
            String codigoPlan,
            boolean operativa,
            BigDecimal ventas,
            int ordenes,
            BigDecimal saldoPendiente,
            Alertas alertas) {
    }
}
