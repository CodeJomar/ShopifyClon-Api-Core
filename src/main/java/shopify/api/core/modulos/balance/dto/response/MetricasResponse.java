package shopify.api.core.modulos.balance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Métricas del panel (MET-001) en la zona horaria de la tienda. */
public record MetricasResponse(
        String moneda,
        String zonaHoraria,
        LocalDate desde,
        LocalDate hasta,
        ResumenResponse resumen,
        List<DiaResponse> porDia,
        List<TopProductoResponse> topProductos,
        long ordenesPorPreparar) {

    /** Cifras acumuladas del balance (BAL-001, BAL-002). */
    public record ResumenResponse(
            BigDecimal ventasHistoricas,
            BigDecimal reembolsado,
            BigDecimal comisiones,
            BigDecimal saldoPorCobrar,
            int ordenesPagadas) {
    }

    /** Un día del rango. Se incluyen también los días sin ventas, con ceros. */
    public record DiaResponse(
            LocalDate fecha,
            BigDecimal ventasBrutas,
            BigDecimal reembolsos,
            BigDecimal ventasNetas,
            int ordenesPagadas,
            BigDecimal ticketPromedio) {
    }

    /** Producto del rango por unidades vendidas. */
    public record TopProductoResponse(
            UUID varianteId,
            String tituloProducto,
            String tituloVariante,
            long unidades,
            BigDecimal importe) {
    }
}
