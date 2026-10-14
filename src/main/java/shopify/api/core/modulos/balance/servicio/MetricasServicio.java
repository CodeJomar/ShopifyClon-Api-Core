package shopify.api.core.modulos.balance.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.balance.dto.response.ComisionTiendaResponse;
import shopify.api.core.modulos.balance.dto.response.MetricasResponse;
import shopify.api.core.modulos.balance.dto.response.MetricasResponse.DiaResponse;
import shopify.api.core.modulos.balance.dto.response.MetricasResponse.ResumenResponse;
import shopify.api.core.modulos.balance.dto.response.MetricasResponse.TopProductoResponse;
import shopify.api.core.modulos.balance.repositorio.MetricasRepositorio;

/**
 * Métricas del panel (MET-001) y comisiones de plataforma. Solo lectura: nada de lo que hace escribe datos.
 * Los días se cuentan en la zona horaria de la tienda, y los cargos por la fecha de su pago aprobado.
 */
@Service
@RequiredArgsConstructor
public class MetricasServicio {

    static final int DIAS_POR_OMISION = 30;
    static final int MAX_DIAS = 366;
    static final int TOP_PRODUCTOS = 5;
    static final int TAMANO_PAGINA = 20;

    private final MetricasRepositorio repositorio;
    private final Clock reloj;

    /** Métricas de la tienda en un rango de días (por omisión, los últimos 30 días hasta hoy en su zona). */
    @Transactional(readOnly = true)
    public MetricasResponse panel(UUID tiendaId, LocalDate desdeSolicitado, LocalDate hastaSolicitado) {
        Map<String, Object> tienda = repositorio.infoTienda(tiendaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));
        String zona = (String) tienda.get("ZonaHoraria");
        String moneda = (String) tienda.get("Moneda");
        ZoneId zoneId = ZoneId.of(zona);

        LocalDate hasta = hastaSolicitado != null ? hastaSolicitado : LocalDate.now(reloj.withZone(zoneId));
        LocalDate desde = desdeSolicitado != null ? desdeSolicitado : hasta.minusDays(DIAS_POR_OMISION - 1L);
        if (desde.isAfter(hasta)) {
            throw new ReglaNegocioException("MET-001", "El inicio del rango no puede ser posterior al fin");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS) {
            throw new ReglaNegocioException("MET-001", "El rango no puede superar " + MAX_DIAS + " días");
        }

        Instant inicio = desde.atStartOfDay(zoneId).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zoneId).toInstant();
        return new MetricasResponse(moneda, zona, desde, hasta, resumen(tiendaId), dias(zona, tiendaId, desde, hasta, inicio, fin),
                topProductos(tiendaId, inicio, fin), repositorio.ordenesPorPreparar(tiendaId));
    }

    /** Comisiones por tienda, de mayor a menor, paginado de 20 (solo administrador de plataforma: lo exige el controlador). */
    @Transactional(readOnly = true)
    public Page<ComisionTiendaResponse> comisionesPorTienda(int pagina) {
        int p = Math.max(pagina, 0);
        List<ComisionTiendaResponse> filas = repositorio.comisionesPorTienda(TAMANO_PAGINA, (long) p * TAMANO_PAGINA).stream()
                .map(fila -> new ComisionTiendaResponse((UUID) fila.get("Id"), (String) fila.get("Nombre"),
                        (String) fila.get("Subdominio"), (String) fila.get("plan"), dinero(fila.get("TotalComisiones")),
                        dinero(fila.get("TotalVentasHistorico"))))
                .toList();
        return new PageImpl<>(filas, PageRequest.of(p, TAMANO_PAGINA), repositorio.contarComisionesPorTienda());
    }

    private ResumenResponse resumen(UUID tiendaId) {
        Map<String, Object> balance = repositorio.balance(tiendaId).orElse(Map.of());
        return new ResumenResponse(dinero(balance.get("TotalVentasHistorico")), dinero(balance.get("TotalReembolsado")),
                dinero(balance.get("TotalComisiones")), dinero(balance.get("SaldoPendienteCobro")),
                balance.get("CantidadOrdenes") == null ? 0 : ((Number) balance.get("CantidadOrdenes")).intValue());
    }

    /** Un registro por día del rango, incluidos los días sin ventas (con ceros). */
    private List<DiaResponse> dias(String zona, UUID tiendaId, LocalDate desde, LocalDate hasta, Instant inicio, Instant fin) {
        Map<LocalDate, Map<String, Object>> cargos = porFecha(repositorio.cargosPorDia(zona, tiendaId, inicio, fin));
        Map<LocalDate, Map<String, Object>> reembolsos = porFecha(repositorio.reembolsosPorDia(zona, tiendaId, inicio, fin));
        List<DiaResponse> dias = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            BigDecimal brutas = dinero(cargos.getOrDefault(dia, Map.of()).get("importe"));
            BigDecimal reembolso = dinero(reembolsos.getOrDefault(dia, Map.of()).get("importe"));
            int ordenes = cargos.getOrDefault(dia, Map.of()).get("ordenes") == null ? 0
                    : ((Number) cargos.get(dia).get("ordenes")).intValue();
            BigDecimal ticket = ordenes == 0 ? BigDecimal.ZERO.setScale(2)
                    : brutas.divide(BigDecimal.valueOf(ordenes), 2, RoundingMode.HALF_UP);
            dias.add(new DiaResponse(dia, brutas, reembolso, brutas.subtract(reembolso), ordenes, ticket));
        }
        return dias;
    }

    private List<TopProductoResponse> topProductos(UUID tiendaId, Instant inicio, Instant fin) {
        List<TopProductoResponse> top = new ArrayList<>();
        for (Map<String, Object> fila : repositorio.topProductos(tiendaId, inicio, fin, TOP_PRODUCTOS)) {
            top.add(new TopProductoResponse((UUID) fila.get("VarianteId"), (String) fila.get("titulo"),
                    (String) fila.get("variante"), ((Number) fila.get("unidades")).longValue(), dinero(fila.get("importe"))));
        }
        return top;
    }

    private static Map<LocalDate, Map<String, Object>> porFecha(List<Map<String, Object>> filas) {
        Map<LocalDate, Map<String, Object>> porFecha = new LinkedHashMap<>();
        for (Map<String, Object> fila : filas) {
            Object dia = fila.get("dia");
            LocalDate fecha = dia instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(String.valueOf(dia));
            porFecha.put(fecha, fila);
        }
        return porFecha;
    }

    /** Importes con 2 decimales y redondeo HALF_UP (regla de Dinero); un nulo es cero. */
    private static BigDecimal dinero(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return new BigDecimal(valor.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
