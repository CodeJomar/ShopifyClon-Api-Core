package shopify.api.core.modulos.inventario.servicio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.inventario.entidad.InventarioVarianteEntidad;
import shopify.api.core.modulos.inventario.entidad.MovimientoInventarioEntidad;
import shopify.api.core.modulos.inventario.modelo.Cantidades;
import shopify.api.core.modulos.inventario.modelo.Disponibilidad;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.modelo.TipoMovimiento;
import shopify.api.core.modulos.inventario.repositorio.InventarioVarianteRepositorio;
import shopify.api.core.modulos.inventario.repositorio.MovimientoInventarioRepositorio;

/**
 * Único módulo que cambia las cantidades (INV-008). Las operaciones de venta son internas y van dentro de la
 * transacción del checkout o del pago; bloquean las filas en orden de {@code VarianteId} (INV-009).
 */
@Service
@RequiredArgsConstructor
public class InventarioServicio {

    private static final String ENTIDAD = "InventarioVariante";
    private static final Instant MAXIMO = Instant.parse("9999-12-31T23:59:59Z");

    private final InventarioVarianteRepositorio inventarios;
    private final MovimientoInventarioRepositorio movimientos;
    private final AuditoriaServicio auditoria;

    /** INV-008: si la cantidad inicial es positiva, deja un movimiento {@code INICIAL} en la misma transacción. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void crearInicial(UUID tiendaId, UUID varianteId, int cantidad, boolean rastrea, boolean ventaSinStock) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La cantidad inicial no puede ser negativa");
        }
        InventarioVarianteEntidad inventario = new InventarioVarianteEntidad();
        inventario.setVarianteId(varianteId);
        inventario.setCantidadDisponible(cantidad);
        inventario.setCantidadReservada(0);
        inventario.setRastreaInventario(rastrea);
        inventario.setPermitirVentaSinStock(ventaSinStock);
        inventarios.saveAndFlush(inventario);

        if (cantidad > 0) {
            registrar(tiendaId, varianteId, TipoMovimiento.INICIAL, cantidad, 0, cantidad, 0, null, null);
        }
    }

    /**
     * INV-002, todo o nada: si alguna línea que rastrea no cabe, se lanza un único 409 con todas las variantes
     * afectadas y no se reserva nada. Con venta sin stock siempre reserva.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reservar(UUID tiendaId, List<LineaInventario> lineas, UUID ordenId) {
        Map<UUID, Integer> pedido = consolidar(lineas);
        Map<UUID, InventarioVarianteEntidad> filas = bloquear(tiendaId, pedido.keySet());

        List<Map<String, Object>> faltantes = new ArrayList<>();
        for (Map.Entry<UUID, Integer> linea : pedido.entrySet()) {
            InventarioVarianteEntidad fila = filas.get(linea.getKey());
            if (!rastrea(fila)) {
                continue;
            }
            int vendible = vendible(fila);
            if (!ventaSinStock(fila) && vendible < linea.getValue()) {
                faltantes.add(Map.of("variante_id", linea.getKey(), "solicitado", linea.getValue(),
                        "disponible", vendible));
            }
        }
        if (!faltantes.isEmpty()) {
            throw new ConflictoEstadoException("INV-002", "No hay stock suficiente para reservar",
                    Map.of("variantes", faltantes));
        }

        for (Map.Entry<UUID, Integer> linea : pedido.entrySet()) {
            InventarioVarianteEntidad fila = filas.get(linea.getKey());
            if (!rastrea(fila)) {
                continue;
            }
            int q = linea.getValue();
            fila.setCantidadReservada(fila.getCantidadReservada() + q);
            inventarios.save(fila);
            registrar(tiendaId, fila.getVarianteId(), TipoMovimiento.RESERVA, 0, q,
                    fila.getCantidadDisponible(), fila.getCantidadReservada(), ordenId, null);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void liberar(UUID tiendaId, List<LineaInventario> lineas, UUID ordenId) {
        Map<UUID, Integer> pedido = consolidar(lineas);
        Map<UUID, InventarioVarianteEntidad> filas = bloquear(tiendaId, pedido.keySet());
        for (Map.Entry<UUID, Integer> linea : pedido.entrySet()) {
            InventarioVarianteEntidad fila = filas.get(linea.getKey());
            if (rastrea(fila) && fila.getCantidadReservada() - linea.getValue() < 0) {
                throw new IllegalStateException("No se puede liberar más de lo reservado");
            }
        }
        for (Map.Entry<UUID, Integer> linea : pedido.entrySet()) {
            InventarioVarianteEntidad fila = filas.get(linea.getKey());
            if (!rastrea(fila)) {
                continue;
            }
            int q = linea.getValue();
            fila.setCantidadReservada(fila.getCantidadReservada() - q);
            inventarios.save(fila);
            registrar(tiendaId, fila.getVarianteId(), TipoMovimiento.LIBERACION, 0, -q,
                    fila.getCantidadDisponible(), fila.getCantidadReservada(), ordenId, null);
        }
    }

    /** Baja lo reservado y lo disponible: la venta ya se hizo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmarVenta(UUID tiendaId, List<LineaInventario> lineas, UUID ordenId) {
        Map<UUID, Integer> pedido = consolidar(lineas);
        Map<UUID, InventarioVarianteEntidad> filas = bloquear(tiendaId, pedido.keySet());
        for (Map.Entry<UUID, Integer> linea : pedido.entrySet()) {
            InventarioVarianteEntidad fila = filas.get(linea.getKey());
            if (rastrea(fila) && fila.getCantidadReservada() < linea.getValue()) {
                throw new IllegalStateException("La venta supera lo reservado");
            }
        }
        for (Map.Entry<UUID, Integer> linea : pedido.entrySet()) {
            InventarioVarianteEntidad fila = filas.get(linea.getKey());
            if (!rastrea(fila)) {
                continue;
            }
            int q = linea.getValue();
            fila.setCantidadDisponible(fila.getCantidadDisponible() - q);
            fila.setCantidadReservada(fila.getCantidadReservada() - q);
            inventarios.save(fila);
            registrar(tiendaId, fila.getVarianteId(), TipoMovimiento.VENTA, -q, -q,
                    fila.getCantidadDisponible(), fila.getCantidadReservada(), ordenId, null);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reponer(UUID tiendaId, List<LineaInventario> lineas, UUID ordenId, String motivo) {
        Map<UUID, Integer> pedido = consolidar(lineas);
        Map<UUID, InventarioVarianteEntidad> filas = bloquear(tiendaId, pedido.keySet());
        for (Map.Entry<UUID, Integer> linea : pedido.entrySet()) {
            InventarioVarianteEntidad fila = filas.get(linea.getKey());
            if (!rastrea(fila)) {
                continue;
            }
            int q = linea.getValue();
            fila.setCantidadDisponible(fila.getCantidadDisponible() + q);
            inventarios.save(fila);
            registrar(tiendaId, fila.getVarianteId(), TipoMovimiento.REPOSICION, q, 0,
                    fila.getCantidadDisponible(), fila.getCantidadReservada(), ordenId, motivo);
        }
    }

    /**
     * INV-007: fija la cantidad disponible; no puede quedar por debajo de lo reservado. Sin cambio no escribe nada.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ajustar(UUID tiendaId, UUID varianteId, int nuevaCantidad, String motivo) {
        InventarioVarianteEntidad fila = bloquear(tiendaId, List.of(varianteId)).get(varianteId);
        if (nuevaCantidad < fila.getCantidadReservada()) {
            throw new ReglaNegocioException("INV-007",
                    "No puedes dejar menos unidades (" + nuevaCantidad + ") que las reservadas ("
                            + fila.getCantidadReservada() + ")");
        }
        int antes = fila.getCantidadDisponible();
        int delta = nuevaCantidad - antes;
        if (delta == 0) {
            return;
        }
        fila.setCantidadDisponible(nuevaCantidad);
        inventarios.save(fila);
        registrar(tiendaId, varianteId, TipoMovimiento.AJUSTE, delta, 0, nuevaCantidad,
                fila.getCantidadReservada(), null, motivo);
        auditoria.registrar(tiendaId, AccionAuditoria.INVENTARIO_AJUSTADO, ENTIDAD, fila.getId(), motivo,
                Map.of("antes", Map.of("disponible", antes), "despues", Map.of("disponible", nuevaCantidad)));
    }

    /**
     * Configuración de rastreo y venta sin stock. Quitar la venta sin stock con disponible negativo se rechaza
     * (INV-007): hay que reponer antes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void configurar(UUID tiendaId, UUID varianteId, Boolean rastrea, Boolean ventaSinStock) {
        InventarioVarianteEntidad fila = bloquear(tiendaId, List.of(varianteId)).get(varianteId);
        if (Boolean.FALSE.equals(ventaSinStock) && fila.getCantidadDisponible() < 0) {
            throw new ReglaNegocioException("INV-007",
                    "Repón " + (-fila.getCantidadDisponible()) + " unidades antes de desactivar la venta sin stock");
        }
        if (rastrea != null) {
            fila.setRastreaInventario(rastrea);
        }
        if (ventaSinStock != null) {
            fila.setPermitirVentaSinStock(ventaSinStock);
        }
        inventarios.save(fila);
    }

    /** Lectura del panel: una variante de otra tienda no existe (INV-404). */
    @Transactional(readOnly = true)
    public Cantidades detalle(UUID tiendaId, UUID varianteId) {
        InventarioVarianteEntidad fila = inventarios.buscarDeTienda(tiendaId, varianteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("INV-404", "No encontramos el inventario de esa variante"));
        return new Cantidades(fila.getCantidadDisponible(), fila.getCantidadReservada(),
                Boolean.TRUE.equals(fila.getRastreaInventario()), Boolean.TRUE.equals(fila.getPermitirVentaSinStock()));
    }

    /** Sin filtro, los límites son los extremos del tiempo (ver {@code MovimientoInventarioRepositorio}). */
    @Transactional(readOnly = true)
    public Page<MovimientoInventarioEntidad> movimientos(UUID tiendaId, UUID varianteId, Instant desde, Instant hasta,
                                                         Pageable pagina) {
        detalle(tiendaId, varianteId);
        return movimientos.historial(tiendaId, varianteId,
                desde == null ? Instant.EPOCH : desde,
                hasta == null ? MAXIMO : hasta, pagina);
    }

    /** Variantes sin fila de inventario no aparecen en el mapa. */
    @Transactional(readOnly = true)
    public Map<UUID, Disponibilidad> disponibilidad(Collection<UUID> varianteIds) {
        Map<UUID, Disponibilidad> resultado = new HashMap<>();
        if (varianteIds == null || varianteIds.isEmpty()) {
            return resultado;
        }
        for (InventarioVarianteEntidad inventario : inventarios.findByVarianteIdInAndEliminadoFalse(varianteIds)) {
            resultado.put(inventario.getVarianteId(), aDisponibilidad(inventario));
        }
        return resultado;
    }

    /** Cantidades crudas para el panel. Variantes sin fila de inventario no aparecen. */
    @Transactional(readOnly = true)
    public Map<UUID, Cantidades> cantidades(Collection<UUID> varianteIds) {
        Map<UUID, Cantidades> resultado = new HashMap<>();
        if (varianteIds == null || varianteIds.isEmpty()) {
            return resultado;
        }
        for (InventarioVarianteEntidad inventario : inventarios.findByVarianteIdInAndEliminadoFalse(varianteIds)) {
            resultado.put(inventario.getVarianteId(), new Cantidades(inventario.getCantidadDisponible(),
                    inventario.getCantidadReservada(), Boolean.TRUE.equals(inventario.getRastreaInventario()),
                    Boolean.TRUE.equals(inventario.getPermitirVentaSinStock())));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public Optional<Disponibilidad> de(UUID varianteId) {
        return inventarios.findFirstByVarianteIdAndEliminadoFalse(varianteId).map(InventarioServicio::aDisponibilidad);
    }

    /** Bloquea las filas de la tienda en orden de {@code VarianteId}; si falta alguna, INV-404. */
    private Map<UUID, InventarioVarianteEntidad> bloquear(UUID tiendaId, Collection<UUID> varianteIds) {
        List<InventarioVarianteEntidad> filas = inventarios.bloquearPorVariantes(tiendaId, varianteIds);
        if (filas.size() != varianteIds.size()) {
            throw new RecursoNoEncontradoException("INV-404", "No encontramos el inventario de alguna variante");
        }
        Map<UUID, InventarioVarianteEntidad> porVariante = new LinkedHashMap<>();
        for (InventarioVarianteEntidad fila : filas) {
            porVariante.put(fila.getVarianteId(), fila);
        }
        return porVariante;
    }

    /** Líneas repetidas se suman; cantidades no positivas son un error de programación. */
    private static Map<UUID, Integer> consolidar(List<LineaInventario> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("Indica al menos una variante");
        }
        Map<UUID, Integer> pedido = new java.util.TreeMap<>();
        for (LineaInventario linea : lineas) {
            if (linea.cantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor que 0");
            }
            pedido.merge(linea.varianteId(), linea.cantidad(), Integer::sum);
        }
        return pedido;
    }

    private void registrar(UUID tiendaId, UUID varianteId, TipoMovimiento tipo, int deltaDisponible, int deltaReservado,
                           int disponibleResultante, int reservadoResultante, UUID ordenId, String motivo) {
        MovimientoInventarioEntidad movimiento = new MovimientoInventarioEntidad();
        movimiento.setTiendaId(tiendaId);
        movimiento.setVarianteId(varianteId);
        movimiento.setTipo(tipo);
        movimiento.setDeltaDisponible(deltaDisponible);
        movimiento.setDeltaReservado(deltaReservado);
        movimiento.setDisponibleResultante(disponibleResultante);
        movimiento.setReservadoResultante(reservadoResultante);
        movimiento.setOrdenId(ordenId);
        movimiento.setMotivo(motivo);
        movimientos.save(movimiento);
    }

    private static boolean rastrea(InventarioVarianteEntidad fila) {
        return Boolean.TRUE.equals(fila.getRastreaInventario());
    }

    private static boolean ventaSinStock(InventarioVarianteEntidad fila) {
        return Boolean.TRUE.equals(fila.getPermitirVentaSinStock());
    }

    private static int vendible(InventarioVarianteEntidad fila) {
        return fila.getCantidadDisponible() - fila.getCantidadReservada();
    }

    /** INV-001 y la regla de disponibilidad: sin rastreo o con venta sin stock siempre está disponible. */
    private static Disponibilidad aDisponibilidad(InventarioVarianteEntidad inventario) {
        int vendible = vendible(inventario);
        boolean rastrea = rastrea(inventario);
        boolean ventaSinStock = ventaSinStock(inventario);
        boolean disponible = !rastrea || ventaSinStock || vendible > 0;
        return new Disponibilidad(vendible, rastrea, ventaSinStock, disponible);
    }
}
