package shopify.api.core.modulos.inventario.servicio;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modulos.inventario.entidad.InventarioVarianteEntidad;
import shopify.api.core.modulos.inventario.entidad.MovimientoInventarioEntidad;
import shopify.api.core.modulos.inventario.modelo.Disponibilidad;
import shopify.api.core.modulos.inventario.modelo.TipoMovimiento;
import shopify.api.core.modulos.inventario.repositorio.InventarioVarianteRepositorio;
import shopify.api.core.modulos.inventario.repositorio.MovimientoInventarioRepositorio;

/**
 * Cantidades de las variantes. Base del módulo: alta de inventario inicial y disponibilidad para la vitrina.
 * Ajustes, reservas y ventas llegan en T-009.
 */
@Service
@RequiredArgsConstructor
public class InventarioServicio {

    private final InventarioVarianteRepositorio inventarios;
    private final MovimientoInventarioRepositorio movimientos;

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
            MovimientoInventarioEntidad movimiento = new MovimientoInventarioEntidad();
            movimiento.setTiendaId(tiendaId);
            movimiento.setVarianteId(varianteId);
            movimiento.setTipo(TipoMovimiento.INICIAL);
            movimiento.setDeltaDisponible(cantidad);
            movimiento.setDeltaReservado(0);
            movimiento.setDisponibleResultante(cantidad);
            movimiento.setReservadoResultante(0);
            movimientos.save(movimiento);
        }
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

    @Transactional(readOnly = true)
    public Optional<Disponibilidad> de(UUID varianteId) {
        return inventarios.findFirstByVarianteIdAndEliminadoFalse(varianteId).map(InventarioServicio::aDisponibilidad);
    }

    /** INV-001 y la regla de disponibilidad: sin rastreo o con venta sin stock siempre está disponible. */
    private static Disponibilidad aDisponibilidad(InventarioVarianteEntidad inventario) {
        int vendible = inventario.getCantidadDisponible() - inventario.getCantidadReservada();
        boolean rastrea = Boolean.TRUE.equals(inventario.getRastreaInventario());
        boolean ventaSinStock = Boolean.TRUE.equals(inventario.getPermitirVentaSinStock());
        boolean disponible = !rastrea || ventaSinStock || vendible > 0;
        return new Disponibilidad(vendible, rastrea, ventaSinStock, disponible);
    }
}
