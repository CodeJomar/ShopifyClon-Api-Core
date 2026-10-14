package shopify.api.core.modulos.pago.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.pago.modelo.EstadoRegistroPago;
import shopify.api.core.modulos.pago.modelo.TipoPago;

/** Registro de un cargo o un reembolso (tabla {@code Pagos}). Solo se añade; nunca se edita el monto. */
@Getter
@Setter
@Entity
@Table(name = "\"Pagos\"")
public class PagoEntidad extends AuditoriaEntidad {

    @Column(name = "\"OrdenId\"", nullable = false)
    private UUID ordenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"PasarelaPago\"", nullable = false, length = 50)
    private PasarelaPago pasarelaPago;

    /** Identificador de la transacción en la pasarela; nulo en los pagos manuales. */
    @Column(name = "\"TransaccionId\"", length = 255)
    private String transaccionId;

    @Column(name = "\"Monto\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "\"Moneda\"", nullable = false, length = 3)
    private String moneda;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Estado\"", nullable = false, length = 30)
    private EstadoRegistroPago estado;

    /** Resumen de la respuesta de la pasarela, sin datos de tarjeta (PAG-009). Nulo en pagos manuales. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "\"DetallesRespuesta\"")
    private Map<String, Object> detallesRespuesta;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Tipo\"", nullable = false, length = 20)
    private TipoPago tipo = TipoPago.CARGO;

    /** Para un reembolso: el cargo que se devuelve. */
    @Column(name = "\"PagoOrigenId\"")
    private UUID pagoOrigenId;

    /** Número de operación que da el personal en un pago manual. */
    @Column(name = "\"Referencia\"", length = 100)
    private String referencia;

    @Column(name = "\"Motivo\"", length = 255)
    private String motivo;
}
