package shopify.api.core.modulos.propietario.servicio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.balance.dto.response.MetricasResponse;
import shopify.api.core.modulos.balance.dto.response.MetricasResponse.DiaResponse;
import shopify.api.core.modulos.balance.servicio.MetricasServicio;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse.Alertas;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse.PuntoDia;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse.TiendaResumen;
import shopify.api.core.modulos.propietario.dto.response.ResumenPropietarioResponse.TotalMoneda;
import shopify.api.core.modulos.propietario.repositorio.AlertasPropietarioRepositorio;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionVigente;
import shopify.api.core.modulos.suscripcion.servicio.SuscripcionServicio;
import shopify.api.core.modulos.tienda.dto.response.MiTiendaResponse;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/**
 * Panel general del propietario (R-001, B7): reutiliza las métricas de cada tienda suya y las junta, sin necesitar una
 * tienda activa. Solo lectura. Un tope de tiendas por persona acota el costo de la consulta.
 */
@Service
@RequiredArgsConstructor
public class ResumenPropietarioServicio {

    static final int MAX_TIENDAS = 50;
    private static final String PROPIETARIO = "PROPIETARIO";

    private final TiendaServicio tiendas;
    private final MetricasServicio metricas;
    private final SuscripcionServicio suscripciones;
    private final AlertasPropietarioRepositorio alertas;

    @Transactional(readOnly = true)
    public ResumenPropietarioResponse resumen(LocalDate desde, LocalDate hasta) {
        UUID propietario = SesionActual.usuarioId();
        List<MiTiendaResponse> propias = tiendas.misTiendas().stream()
                .filter(t -> PROPIETARIO.equals(t.rol())).limit(MAX_TIENDAS).toList();
        Map<UUID, Long> stockBajo = alertas.stockBajoPorTienda(propias.stream().map(MiTiendaResponse::tiendaId).toList());

        List<TiendaResumen> filas = new ArrayList<>();
        Map<String, BigDecimal[]> totales = new LinkedHashMap<>();           // moneda -> {ventas, saldo}
        Map<String, Integer> ordenesPorMoneda = new LinkedHashMap<>();
        Map<String, Map<LocalDate, Object[]>> serie = new LinkedHashMap<>();  // moneda -> fecha -> {ventas, ordenes}
        LocalDate rangoDesde = desde;
        LocalDate rangoHasta = hasta;

        for (MiTiendaResponse tienda : propias) {
            MetricasResponse m = metricas.panel(tienda.tiendaId(), desde, hasta);
            rangoDesde = m.desde();
            rangoHasta = m.hasta();
            BigDecimal ventas = BigDecimal.ZERO.setScale(2);
            int ordenes = 0;
            for (DiaResponse dia : m.porDia()) {
                ventas = ventas.add(dia.ventasNetas());
                ordenes += dia.ordenesPagadas();
                Object[] punto = serie.computeIfAbsent(m.moneda(), k -> new java.util.TreeMap<>())
                        .computeIfAbsent(dia.fecha(), k -> new Object[]{BigDecimal.ZERO.setScale(2), 0});
                punto[0] = ((BigDecimal) punto[0]).add(dia.ventasNetas());
                punto[1] = (Integer) punto[1] + dia.ordenesPagadas();
            }
            BigDecimal saldo = m.resumen().saldoPorCobrar();
            BigDecimal[] acumulado = totales.computeIfAbsent(m.moneda(),
                    k -> new BigDecimal[]{BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)});
            acumulado[0] = acumulado[0].add(ventas);
            acumulado[1] = acumulado[1].add(saldo);
            ordenesPorMoneda.merge(m.moneda(), ordenes, Integer::sum);

            SuscripcionVigente suscripcion = suscripciones.vigente(tienda.tiendaId()).orElse(null);
            filas.add(new TiendaResumen(tienda.tiendaId(), tienda.nombre(), tienda.subdominio(), m.moneda(),
                    suscripcion == null ? null : suscripcion.estado().name(),
                    suscripcion == null ? null : suscripcion.codigoPlan(), tienda.operativa(), ventas, ordenes, saldo,
                    new Alertas(m.ordenesPorPreparar(), stockBajo.getOrDefault(tienda.tiendaId(), 0L))));
        }

        List<TotalMoneda> totalesLista = totales.entrySet().stream()
                .map(e -> new TotalMoneda(e.getKey(), e.getValue()[0], ordenesPorMoneda.get(e.getKey()), e.getValue()[1]))
                .toList();
        List<PuntoDia> serieLista = new ArrayList<>();
        serie.forEach((moneda, dias) -> dias.forEach((fecha, valores) ->
                serieLista.add(new PuntoDia(moneda, fecha, (BigDecimal) valores[0], (Integer) valores[1]))));
        return new ResumenPropietarioResponse(rangoDesde, rangoHasta, totalesLista, serieLista, filas);
    }
}
