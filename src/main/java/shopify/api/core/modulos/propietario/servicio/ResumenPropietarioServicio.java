package shopify.api.core.modulos.propietario.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse.Alertas;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse.PuntoDia;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse.TiendaResumen;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse.TotalMoneda;
import shopify.api.core.modulos.propietario.repositorio.AlertasPropietarioRepositorio;
import shopify.api.core.modulos.propietario.repositorio.AlertasPropietarioRepositorio.RangoTienda;
import shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion;

/**
 * Panel general del propietario (R-001, B7): ventas, saldo, suscripción y alertas de todas sus tiendas, sin necesitar
 * una tienda activa. Solo lectura. Hace un número fijo de consultas (una por tema para todas las tiendas a la vez), no
 * varias por tienda. Las fechas se cuentan en la zona horaria de cada tienda, como las métricas del panel de tienda.
 */
@Service
@RequiredArgsConstructor
public class ResumenPropietarioServicio {

    static final int MAX_TIENDAS = 50;
    static final int DIAS_POR_OMISION = 30;
    static final int MAX_DIAS = 366;

    private final AlertasPropietarioRepositorio repositorio;
    private final Clock reloj;

    private record Ventas(BigDecimal bruto, BigDecimal reembolsos, int ordenes) {
    }

    public ResumenPropietarioResponse resumen(LocalDate desdeSolicitado, LocalDate hastaSolicitado) {
        UUID propietario = SesionActual.usuarioId();
        List<Map<String, Object>> tiendas = repositorio.tiendasDelPropietario(propietario, MAX_TIENDAS);
        List<UUID> ids = tiendas.stream().map(t -> (UUID) t.get("Id")).toList();

        // Rango de cada tienda en su zona (mismas reglas que las métricas del panel: MET-001)
        Map<UUID, LocalDate[]> fechas = new HashMap<>();
        List<RangoTienda> rangos = new ArrayList<>();
        for (Map<String, Object> t : tiendas) {
            UUID id = (UUID) t.get("Id");
            ZoneId zona = ZoneId.of((String) t.get("ZonaHoraria"));
            LocalDate hasta = hastaSolicitado != null ? hastaSolicitado : LocalDate.now(reloj.withZone(zona));
            LocalDate desde = desdeSolicitado != null ? desdeSolicitado : hasta.minusDays(DIAS_POR_OMISION - 1L);
            if (desde.isAfter(hasta)) {
                throw new ReglaNegocioException("MET-001", "El inicio del rango no puede ser posterior al fin");
            }
            if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS) {
                throw new ReglaNegocioException("MET-001", "El rango no puede superar " + MAX_DIAS + " días");
            }
            fechas.put(id, new LocalDate[]{desde, hasta});
            rangos.add(new RangoTienda(id, zona.getId(), desde.atStartOfDay(zona).toInstant(),
                    hasta.plusDays(1).atStartOfDay(zona).toInstant()));
        }

        Map<UUID, BigDecimal> saldos = repositorio.saldos(ids);
        Map<UUID, Long> porPreparar = repositorio.ordenesPorPreparar(ids);
        Map<UUID, Long> stockBajo = repositorio.stockBajoPorTienda(ids);
        Map<UUID, Map<LocalDate, Ventas>> ventasPorTienda = ventasPorDia(rangos);

        List<TiendaResumen> filas = new ArrayList<>();
        Map<String, BigDecimal[]> totales = new LinkedHashMap<>();           // moneda -> {ventas, saldo}
        Map<String, Integer> ordenesPorMoneda = new LinkedHashMap<>();
        Map<String, Map<LocalDate, Object[]>> serie = new LinkedHashMap<>();  // moneda -> fecha -> {ventas, ordenes}
        LocalDate rangoDesde = desdeSolicitado;
        LocalDate rangoHasta = hastaSolicitado;

        for (Map<String, Object> t : tiendas) {
            UUID id = (UUID) t.get("Id");
            String moneda = (String) t.get("Moneda");
            LocalDate[] rango = fechas.get(id);
            rangoDesde = rango[0];
            rangoHasta = rango[1];
            Map<LocalDate, Ventas> dias = ventasPorTienda.getOrDefault(id, Map.of());

            BigDecimal ventas = dinero(null);
            int ordenes = 0;
            for (LocalDate dia = rango[0]; !dia.isAfter(rango[1]); dia = dia.plusDays(1)) {
                Ventas v = dias.get(dia);
                BigDecimal neto = v == null ? dinero(null) : v.bruto().subtract(v.reembolsos());
                int pagadas = v == null ? 0 : v.ordenes();
                ventas = ventas.add(neto);
                ordenes += pagadas;
                Object[] punto = serie.computeIfAbsent(moneda, k -> new TreeMap<>())
                        .computeIfAbsent(dia, k -> new Object[]{dinero(null), 0});
                punto[0] = ((BigDecimal) punto[0]).add(neto);
                punto[1] = (Integer) punto[1] + pagadas;
            }
            BigDecimal saldo = dinero(saldos.get(id));
            BigDecimal[] acumulado = totales.computeIfAbsent(moneda, k -> new BigDecimal[]{dinero(null), dinero(null)});
            acumulado[0] = acumulado[0].add(ventas);
            acumulado[1] = acumulado[1].add(saldo);
            ordenesPorMoneda.merge(moneda, ordenes, Integer::sum);

            String estado = (String) t.get("estado");
            boolean operativa = Boolean.TRUE.equals(t.get("EstaActiva")) && estado != null
                    && EstadoSuscripcion.valueOf(estado).permiteOperar();
            filas.add(new TiendaResumen(id, (String) t.get("Nombre"), (String) t.get("Subdominio"), (String) t.get("Ruc"), moneda, estado,
                    (String) t.get("plan"), operativa, ventas, ordenes, saldo,
                    new Alertas(porPreparar.getOrDefault(id, 0L), stockBajo.getOrDefault(id, 0L))));
        }

        List<TotalMoneda> totalesLista = totales.entrySet().stream()
                .map(e -> new TotalMoneda(e.getKey(), e.getValue()[0], ordenesPorMoneda.get(e.getKey()), e.getValue()[1]))
                .toList();
        List<PuntoDia> serieLista = new ArrayList<>();
        serie.forEach((moneda, dias) -> dias.forEach((fecha, valores) ->
                serieLista.add(new PuntoDia(moneda, fecha, (BigDecimal) valores[0], (Integer) valores[1]))));
        return new ResumenPropietarioResponse(rangoDesde, rangoHasta, totalesLista, serieLista, filas);
    }

    /** Cargos y reembolsos de todas las tiendas, agrupados por tienda y día. */
    private Map<UUID, Map<LocalDate, Ventas>> ventasPorDia(List<RangoTienda> rangos) {
        Map<UUID, Map<LocalDate, BigDecimal[]>> acumulado = new HashMap<>();
        Map<UUID, Map<LocalDate, Integer>> ordenes = new HashMap<>();
        for (Map<String, Object> fila : repositorio.cargosPorDia(rangos)) {
            UUID tienda = (UUID) fila.get("tienda");
            LocalDate dia = fecha(fila.get("dia"));
            acumulado.computeIfAbsent(tienda, k -> new HashMap<>()).computeIfAbsent(dia, k -> new BigDecimal[]{dinero(null), dinero(null)})[0] =
                    dinero(fila.get("importe"));
            ordenes.computeIfAbsent(tienda, k -> new HashMap<>()).put(dia, ((Number) fila.get("ordenes")).intValue());
        }
        for (Map<String, Object> fila : repositorio.reembolsosPorDia(rangos)) {
            UUID tienda = (UUID) fila.get("tienda");
            acumulado.computeIfAbsent(tienda, k -> new HashMap<>()).computeIfAbsent(fecha(fila.get("dia")),
                    k -> new BigDecimal[]{dinero(null), dinero(null)})[1] = dinero(fila.get("importe"));
        }
        Map<UUID, Map<LocalDate, Ventas>> resultado = new HashMap<>();
        acumulado.forEach((tienda, dias) -> {
            Map<LocalDate, Ventas> porDia = new HashMap<>();
            dias.forEach((dia, valores) -> porDia.put(dia, new Ventas(valores[0], valores[1],
                    ordenes.getOrDefault(tienda, Map.of()).getOrDefault(dia, 0))));
            resultado.put(tienda, porDia);
        });
        return resultado;
    }

    private static LocalDate fecha(Object dia) {
        return dia instanceof Date d ? d.toLocalDate() : LocalDate.parse(String.valueOf(dia));
    }

    private static BigDecimal dinero(Object valor) {
        return valor == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(valor.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
