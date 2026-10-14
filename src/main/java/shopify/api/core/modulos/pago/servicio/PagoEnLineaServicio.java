package shopify.api.core.modulos.pago.servicio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.modulos.pago.dto.response.IniciarPagoResponse;
import shopify.api.core.modulos.pago.entidad.PagoEntidad;
import shopify.api.core.modulos.pago.modelo.EstadoRegistroPago;
import shopify.api.core.modulos.pago.modelo.TipoPago;
import shopify.api.core.modulos.pago.pasarela.IntentoPago;
import shopify.api.core.modulos.pago.pasarela.PasarelaEnLinea;
import shopify.api.core.modulos.pago.repositorio.PagoRepositorio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/**
 * PAG-002: el comprador inicia el pago en línea con el token de acceso de su orden. Reutiliza el intento pendiente si
 * ya existe. Las llamadas a la pasarela van fuera de las transacciones con filas bloqueadas (PAG-010).
 */
@Service
@RequiredArgsConstructor
public class PagoEnLineaServicio {

    private final TiendaServicio tiendas;
    private final OrdenServicio ordenes;
    private final PagoRepositorio pagos;
    private final PasarelaEnLinea pasarela;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public IniciarPagoResponse iniciar(String subdominio, String tokenAcceso) {
        UUID tiendaId = tiendas.idOperativaPorSubdominio(subdominio)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));
        OrdenEstado orden = ordenes.leerPorTokenAcceso(tiendaId, tokenAcceso)
                .orElseThrow(() -> new RecursoNoEncontradoException("ORD-404", "No encontramos esa orden"));

        if (orden.pasarela() != PasarelaPago.STRIPE) {
            throw new ConflictoEstadoException("PAG-002", "Esta orden no se paga en línea");
        }
        if (orden.estado() != EstadoOrden.ABIERTA || orden.estadoPago() != EstadoPago.PENDIENTE) {
            throw new ConflictoEstadoException("PAG-002", "Esta orden ya no admite pago");
        }
        Instant ahora = Instant.now(reloj);
        if (orden.fechaExpiracionPago() == null || !orden.fechaExpiracionPago().isAfter(ahora)) {
            throw new ConflictoEstadoException("PAG-002", "El plazo para pagar esta orden venció");
        }

        Optional<PagoEntidad> pendiente = pagos.findFirstByOrdenIdAndPasarelaPagoAndEstadoAndEliminadoFalse(
                orden.id(), PasarelaPago.STRIPE, EstadoRegistroPago.PENDIENTE);
        if (pendiente.isPresent()) {
            IntentoPago intento = pasarela.recuperarIntento(pendiente.get().getTransaccionId());
            return respuesta(intento, pendiente.get().getMonto(), pendiente.get().getMoneda());
        }

        BigDecimal total = orden.total();
        String moneda = tiendas.configuracionVenta(tiendaId).moneda();
        long montoCentavos = total.movePointRight(2).longValueExact();
        // Fuera de transacción: la llamada a Stripe no retiene filas bloqueadas (PAG-010)
        IntentoPago intento = pasarela.crearIntento(orden.id(), tiendaId, montoCentavos, moneda);
        transaccion.executeWithoutResult(estado -> registrarCargo(orden.id(), intento.id(), total, moneda));
        return respuesta(intento, total, moneda);
    }

    /** Cargo PENDIENTE de la orden, ligado al intento de la pasarela (único por pasarela y transacción). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCargo(UUID ordenId, String intentoId, BigDecimal monto, String moneda) {
        PagoEntidad pago = new PagoEntidad();
        pago.setOrdenId(ordenId);
        pago.setPasarelaPago(PasarelaPago.STRIPE);
        pago.setTransaccionId(intentoId);
        pago.setMonto(monto);
        pago.setMoneda(moneda);
        pago.setEstado(EstadoRegistroPago.PENDIENTE);
        pago.setTipo(TipoPago.CARGO);
        pagos.saveAndFlush(pago);
    }

    private static IniciarPagoResponse respuesta(IntentoPago intento, BigDecimal monto, String moneda) {
        return new IniciarPagoResponse(intento.clientSecret(), monto, moneda);
    }
}
