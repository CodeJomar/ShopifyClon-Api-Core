package shopify.api.core.modulos.pago.servicio;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.evento.PublicadorEvento;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.cupon.servicio.CuponServicio;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.modulos.pago.eventos.OrdenCanceladaEvento;

/**
 * ORD-007 y ORD-008: cancelación de órdenes. Sin pago: libera la reserva, devuelve el uso del cupón, anula y audita.
 * Pagada (T-019): reembolsa el total pendiente con reposición opcional y luego cancela. El reembolso va antes y fuera
 * de transacción (PAG-010); si la pasarela rechaza, la orden queda intacta.
 */
@Service
@RequiredArgsConstructor
public class CancelacionOrdenServicio {

    public static final String MOTIVO_PAGO_VENCIDO = "PAGO_VENCIDO";

    private final OrdenServicio ordenes;
    private final InventarioServicio inventario;
    private final CuponServicio cupones;
    private final ReembolsoServicio reembolsos;
    private final AuditoriaServicio auditoria;
    private final PublicadorEvento publicador;
    private final TransactionTemplate transaccion;
    private final JdbcTemplate jdbc;

    /**
     * Cancelación desde el panel. Una orden pagada exige {@code puedeReembolsar} (REEMBOLSOS_EMITIR, si no 403 SEG-002)
     * y estar sin preparar. {@code reponerStock} decide si el reembolso repone las unidades (por defecto, sí).
     */
    public void cancelarPorPersonal(UUID tiendaId, UUID ordenId, String motivo, String detalle,
                                    boolean reponerStock, boolean puedeReembolsar) {
        if ("OTRO".equals(motivo) && (detalle == null || detalle.isBlank())) {
            throw new ReglaNegocioException("VAL-001", "Describe el motivo de la cancelación");
        }
        Map<String, Object> estado = leerEstado(tiendaId, ordenId);
        EstadoPago estadoPago = EstadoPago.valueOf((String) estado.get("EstadoPago"));
        if (estadoPago.estaCobrado()) {
            if (!puedeReembolsar) {
                throw new AccesoDenegadoException("SEG-002", "Tu rol no tiene permiso para reembolsar");
            }
            if (!"ABIERTA".equals(estado.get("Estado"))
                    || !"NO_PREPARADO".equals(estado.get("EstadoPreparacion"))) {
                throw new ConflictoEstadoException("ORD-007", "Para cancelar una orden pagada debe estar sin preparar");
            }
            reembolsos.reembolsarTotal(tiendaId, ordenId, "Cancelación: " + motivo, reponerStock);
            transaccion.executeWithoutResult(estado2 -> cancelarPagada(tiendaId, ordenId, motivo, detalle));
        } else {
            transaccion.executeWithoutResult(estado2 -> anularSinPago(tiendaId, ordenId, motivo, detalle));
        }
    }

    /** ORD-007/008: anula una orden sin pago. Lo usan el personal y el programador de vencimientos. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void anularSinPago(UUID tiendaId, UUID ordenId, String motivo, String detalle) {
        OrdenEstado orden = ordenes.bloquear(tiendaId, ordenId);
        if (orden.estadoPago().estaCobrado()) {
            throw new ConflictoEstadoException("ORD-007", "Para cancelar una orden pagada emite un reembolso");
        }
        if (orden.estado() != EstadoOrden.ABIERTA || orden.estadoPreparacion() != EstadoPreparacion.NO_PREPARADO) {
            throw new ConflictoEstadoException("ORD-007", "Solo se cancela una orden sin pagar y sin preparar");
        }

        List<LineaInventario> lineas = orden.items().stream()
                .map(linea -> new LineaInventario(linea.varianteId(), linea.cantidad()))
                .toList();
        inventario.liberar(tiendaId, lineas, ordenId);
        if (orden.cuponId() != null) {
            cupones.devolverUso(orden.cuponId());
        }
        ordenes.anular(ordenId, motivo, detalle);

        auditoria.registrar(tiendaId, AccionAuditoria.ORDEN_CANCELADA, "Ordenes", ordenId, motivo,
                Map.of("motivo", motivo, "detalle", detalle == null ? "" : detalle));
        publicador.publicar(new OrdenCanceladaEvento(tiendaId, ordenId, motivo));
    }

    /** Cancela una orden ya reembolsada (ABIERTA, NO_PREPARADO y con dinero cobrado). El reembolso lo hizo antes. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelarPagada(UUID tiendaId, UUID ordenId, String motivo, String detalle) {
        ordenes.bloquear(tiendaId, ordenId);
        ordenes.cancelarPagada(ordenId, motivo, detalle);
        auditoria.registrar(tiendaId, AccionAuditoria.ORDEN_CANCELADA, "Ordenes", ordenId, motivo,
                Map.of("motivo", motivo, "detalle", detalle == null ? "" : detalle));
        publicador.publicar(new OrdenCanceladaEvento(tiendaId, ordenId, motivo));
    }

    /** Lectura sin bloqueo para decidir la ruta; la decisión definitiva se repite al bloquear. ORD-404 si no es de la tienda. */
    private Map<String, Object> leerEstado(UUID tiendaId, UUID ordenId) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT \"Estado\", \"EstadoPago\", \"EstadoPreparacion\" FROM \"Ordenes\" "
                        + "WHERE \"Id\" = ? AND \"TiendaId\" = ? AND \"Eliminado\" = FALSE", ordenId, tiendaId);
        if (filas.isEmpty()) {
            throw new RecursoNoEncontradoException("ORD-404", "No encontramos esa orden");
        }
        return filas.get(0);
    }
}
