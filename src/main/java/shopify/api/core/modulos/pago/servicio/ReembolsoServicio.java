package shopify.api.core.modulos.pago.servicio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.balance.servicio.BalanceServicio;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.modulos.pago.dto.response.PagoResponse;
import shopify.api.core.modulos.pago.dto.response.ReembolsoResponse;
import shopify.api.core.modulos.pago.entidad.PagoEntidad;
import shopify.api.core.modulos.pago.modelo.EstadoRegistroPago;
import shopify.api.core.modulos.pago.modelo.LineaReposicion;
import shopify.api.core.modulos.pago.modelo.TipoPago;
import shopify.api.core.modulos.pago.pasarela.PasarelaEnLinea;
import shopify.api.core.modulos.pago.pasarela.ResultadoReembolso;
import shopify.api.core.modulos.pago.repositorio.PagoRepositorio;

/**
 * Reembolsos (PAG-007, PAG-008) con el patrón de PAG-010: planificar en una transacción, llamar a la pasarela fuera de
 * ella, y aplicar los efectos en otra. Si la pasarela rechaza o falla, el reembolso queda RECHAZADO y la orden no cambia.
 */
@Service
@RequiredArgsConstructor
public class ReembolsoServicio {

    private final PagoRepositorio pagos;
    private final OrdenServicio ordenes;
    private final InventarioServicio inventario;
    private final BalanceServicio balance;
    private final AuditoriaServicio auditoria;
    private final PasarelaEnLinea pasarela;
    private final TransactionTemplate transaccion;
    private final JdbcTemplate jdbc;

    /** Reembolso parcial o total desde el panel, con reposición opcional de las unidades indicadas. */
    public ReembolsoResponse reembolsar(UUID tiendaId, UUID ordenId, BigDecimal monto, String motivo,
                                        List<LineaReposicion> reponer) {
        Plan plan = transaccion.execute(estado -> planificar(tiendaId, ordenId, monto, motivo, reponer, false));
        PagoEntidad reembolso = completar(tiendaId, ordenId, plan, motivo);
        return new ReembolsoResponse(respuesta(reembolso), EstadoPago.valueOf(estadoPagoDe(ordenId)));
    }

    /**
     * Reembolso del total pendiente, para cancelar una orden pagada (ORD-007). Si ya no queda nada por reembolsar,
     * no hace nada. Con {@code reponerStock}, repone todas las unidades que aún no se han repuesto.
     */
    public void reembolsarTotal(UUID tiendaId, UUID ordenId, String motivo, boolean reponerStock) {
        Plan plan = transaccion.execute(estado -> planificar(tiendaId, ordenId, null, motivo, List.of(), reponerStock));
        if (plan != null) {
            completar(tiendaId, ordenId, plan, motivo);
        }
    }

    /** Fase 1 (transaccional): valida monto, máximo y reposición, y deja el reembolso PENDIENTE. Null si no hay nada que reembolsar. */
    private Plan planificar(UUID tiendaId, UUID ordenId, BigDecimal montoPedido, String motivo,
                            List<LineaReposicion> reponerPedido, boolean reponerTodo) {
        OrdenEstado orden = ordenes.bloquear(tiendaId, ordenId);
        // PAG-006: una orden anulada con cobro aprobado sí se reembolsa, sin tocar stock, balance ni estado
        boolean tardia = orden.estadoPago() == EstadoPago.ANULADO;
        if (!tardia && orden.estadoPago() != EstadoPago.PAGADO && orden.estadoPago() != EstadoPago.PARCIALMENTE_REEMBOLSADO) {
            throw new ConflictoEstadoException("PAG-007", "Solo se reembolsa una orden pagada");
        }

        List<PagoEntidad> todos = pagos.findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(ordenId);
        List<PagoEntidad> cargos = todos.stream()
                .filter(p -> p.getTipo() == TipoPago.CARGO && p.getEstado() == EstadoRegistroPago.APROBADO)
                .toList();
        if (cargos.isEmpty()) {
            throw new ConflictoEstadoException("PAG-007", "No hay un cobro aprobado que reembolsar");
        }
        BigDecimal cobrado = suma(cargos.stream().map(PagoEntidad::getMonto).toList());
        BigDecimal comprometido = suma(todos.stream()
                .filter(p -> p.getTipo() == TipoPago.REEMBOLSO
                        && (p.getEstado() == EstadoRegistroPago.APROBADO || p.getEstado() == EstadoRegistroPago.PENDIENTE))
                .map(PagoEntidad::getMonto).toList());
        BigDecimal disponible = cobrado.subtract(comprometido);

        if (montoPedido == null && disponible.signum() <= 0) {
            return null;
        }
        BigDecimal monto = montoPedido == null ? disponible : montoPedido;
        if (monto.signum() <= 0 || monto.stripTrailingZeros().scale() > 2) {
            throw new ReglaNegocioException("VAL-001", "El monto debe ser mayor que cero, con hasta 2 decimales");
        }
        if (monto.compareTo(disponible) > 0) {
            throw new ReglaNegocioException("PAG-007", "El monto supera lo reembolsable: el máximo es " + disponible.toPlainString(),
                    Map.of("maximo", disponible.toPlainString()));
        }

        PagoEntidad origen = cargoConSaldo(cargos, todos, monto);
        List<LineaInventario> reposicion = resolverReposicion(orden, reponerPedido, reponerTodo);

        PagoEntidad reembolso = new PagoEntidad();
        reembolso.setOrdenId(ordenId);
        reembolso.setTipo(TipoPago.REEMBOLSO);
        reembolso.setPagoOrigenId(origen.getId());
        reembolso.setPasarelaPago(origen.getPasarelaPago());
        reembolso.setMonto(monto);
        reembolso.setMoneda(origen.getMoneda());
        reembolso.setEstado(EstadoRegistroPago.PENDIENTE);
        reembolso.setMotivo(motivo);
        pagos.saveAndFlush(reembolso);
        return new Plan(reembolso.getId(), origen.getPasarelaPago(), origen.getTransaccionId(), monto,
                origen.getMoneda(), reposicion, tardia);
    }

    /** Fase 2 (fuera de transacción): la pasarela solo se llama para cargos de Stripe; los manuales se devuelven por fuera. */
    private PagoEntidad completar(UUID tiendaId, UUID ordenId, Plan plan, String motivo) {
        ResultadoReembolso resultado = llamarPasarela(plan);
        PagoEntidad reembolso = transaccion.execute(estado -> cerrar(tiendaId, ordenId, plan, resultado, motivo));
        if (reembolso.getEstado() == EstadoRegistroPago.RECHAZADO) {
            throw new ConflictoEstadoException("PAG-007", "La pasarela rechazó el reembolso");
        }
        return reembolso;
    }

    private ResultadoReembolso llamarPasarela(Plan plan) {
        if (plan.pasarela() != PasarelaPago.STRIPE) {
            return new ResultadoReembolso(null, true);
        }
        try {
            return pasarela.reembolsar(plan.transaccionCargo(), plan.monto().movePointRight(2).longValueExact());
        } catch (RuntimeException ex) {
            // Un fallo de red o de configuración deja el reembolso RECHAZADO, nunca PENDIENTE para siempre
            return new ResultadoReembolso(null, false);
        }
    }

    /** Fase 3 (transaccional): registra el resultado y, si fue aprobado, aplica estado, stock, balance y auditoría. */
    private PagoEntidad cerrar(UUID tiendaId, UUID ordenId, Plan plan, ResultadoReembolso resultado, String motivo) {
        PagoEntidad reembolso = pagos.bloquearPorId(plan.reembolsoId())
                .orElseThrow(() -> new IllegalStateException("El reembolso " + plan.reembolsoId() + " debía existir"));
        if (reembolso.getEstado() != EstadoRegistroPago.PENDIENTE) {
            return reembolso;
        }
        if (!resultado.aprobado()) {
            reembolso.setEstado(EstadoRegistroPago.RECHAZADO);
            return pagos.saveAndFlush(reembolso);
        }
        reembolso.setEstado(EstadoRegistroPago.APROBADO);
        reembolso.setTransaccionId(resultado.id());
        pagos.saveAndFlush(reembolso);

        if (!plan.tardia()) {
            ordenes.bloquear(tiendaId, ordenId);
            BigDecimal cobrado = suma(pagos.findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(ordenId).stream()
                    .filter(p -> p.getTipo() == TipoPago.CARGO && p.getEstado() == EstadoRegistroPago.APROBADO)
                    .map(PagoEntidad::getMonto).toList());
            BigDecimal reembolsado = suma(pagos.findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(ordenId).stream()
                    .filter(p -> p.getTipo() == TipoPago.REEMBOLSO && p.getEstado() == EstadoRegistroPago.APROBADO)
                    .map(PagoEntidad::getMonto).toList());
            ordenes.aplicarReembolso(ordenId, reembolsado.compareTo(cobrado) >= 0);
            if (!plan.reposicion().isEmpty()) {
                inventario.reponer(tiendaId, plan.reposicion(), ordenId, motivo);
            }
            balance.registrarReembolso(tiendaId, plan.monto());
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("monto", plan.monto().toPlainString());
        detalle.put("orden_id", ordenId.toString());
        detalle.put("lineas_reposicion", plan.reposicion().size());
        detalle.put("pago_tardio", plan.tardia());
        auditoria.registrar(tiendaId, AccionAuditoria.REEMBOLSO_EMITIDO, "Pagos", reembolso.getId(), motivo, detalle);
        return reembolso;
    }

    /** Cargo al que se imputa el reembolso: el primero con saldo suficiente, sin repartir entre cobros (TODO(Q-T-019)). */
    private PagoEntidad cargoConSaldo(List<PagoEntidad> cargos, List<PagoEntidad> todos, BigDecimal monto) {
        for (PagoEntidad cargo : cargos) {
            BigDecimal ocupado = suma(todos.stream()
                    .filter(p -> p.getTipo() == TipoPago.REEMBOLSO && cargo.getId().equals(p.getPagoOrigenId())
                            && (p.getEstado() == EstadoRegistroPago.APROBADO || p.getEstado() == EstadoRegistroPago.PENDIENTE))
                    .map(PagoEntidad::getMonto).toList());
            if (cargo.getMonto().subtract(ocupado).compareTo(monto) >= 0) {
                return cargo;
            }
        }
        throw new ReglaNegocioException("PAG-007", "El reembolso no cabe en un solo cobro: reembólsalo en partes");
    }

    /**
     * Reposición de stock (INV-006). Valida que cada variante sea de la orden y que no se repongan más unidades de las
     * vendidas. Con {@code reponerTodo}, repone lo que aún falta de cada variante.
     */
    private List<LineaInventario> resolverReposicion(OrdenEstado orden, List<LineaReposicion> pedido, boolean reponerTodo) {
        Map<UUID, Integer> vendidas = new LinkedHashMap<>();
        orden.items().forEach(linea -> vendidas.merge(linea.varianteId(), linea.cantidad(), Integer::sum));
        Map<UUID, Integer> repuestas = repuestasPorVariante(orden.id());
        List<LineaInventario> resultado = new ArrayList<>();

        if (reponerTodo) {
            vendidas.forEach((variante, vendida) -> {
                int falta = vendida - repuestas.getOrDefault(variante, 0);
                if (falta > 0) {
                    resultado.add(new LineaInventario(variante, falta));
                }
            });
            return resultado;
        }
        Map<UUID, Integer> pedidas = new LinkedHashMap<>();
        for (LineaReposicion linea : pedido == null ? List.<LineaReposicion>of() : pedido) {
            pedidas.merge(linea.varianteId(), linea.cantidad(), Integer::sum);
        }
        pedidas.forEach((variante, cantidad) -> {
            if (!vendidas.containsKey(variante)) {
                throw new ReglaNegocioException("PAG-007", "Una variante de la reposición no pertenece a esta orden");
            }
            int disponible = vendidas.get(variante) - repuestas.getOrDefault(variante, 0);
            if (cantidad > disponible) {
                throw new ReglaNegocioException("PAG-007", "Solo se pueden reponer " + disponible + " unidades de una variante");
            }
            resultado.add(new LineaInventario(variante, cantidad));
        });
        return resultado;
    }

    /** Unidades ya repuestas por orden (solo lectura sobre el libro de inventario). */
    private Map<UUID, Integer> repuestasPorVariante(UUID ordenId) {
        Map<UUID, Integer> repuestas = new LinkedHashMap<>();
        jdbc.queryForList("SELECT \"VarianteId\", COALESCE(SUM(\"DeltaDisponible\"), 0) AS total FROM \"MovimientosInventario\" "
                + "WHERE \"OrdenId\" = ? AND \"Tipo\" = 'REPOSICION' AND \"Eliminado\" = FALSE GROUP BY \"VarianteId\"", ordenId)
                .forEach(fila -> repuestas.put((UUID) fila.get("VarianteId"), ((Number) fila.get("total")).intValue()));
        return repuestas;
    }

    private String estadoPagoDe(UUID ordenId) {
        return jdbc.queryForObject("SELECT \"EstadoPago\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, ordenId);
    }

    private static BigDecimal suma(List<BigDecimal> valores) {
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static PagoResponse respuesta(PagoEntidad pago) {
        return new PagoResponse(pago.getId(), pago.getTipo(), pago.getPasarelaPago(), pago.getEstado(), pago.getMonto(),
                pago.getMoneda(), pago.getReferencia(), pago.getMotivo(), pago.getPagoOrigenId(), pago.getFechaCreacion());
    }

    /** Lo que la fase 1 deja listo para la pasarela y la fase 3. */
    private record Plan(UUID reembolsoId, PasarelaPago pasarela, String transaccionCargo, BigDecimal monto, String moneda,
                        List<LineaInventario> reposicion, boolean tardia) {
    }
}
