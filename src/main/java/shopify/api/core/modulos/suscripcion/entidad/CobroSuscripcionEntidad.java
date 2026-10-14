package shopify.api.core.modulos.suscripcion.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.suscripcion.modelo.EstadoCobro;

/** Intento de cobro de un período; solo se añaden filas (SUS-014). */
@Getter
@Setter
@Entity
@Table(name = "\"CobrosSuscripcion\"")
public class CobroSuscripcionEntidad extends AuditoriaEntidad {

    @Column(name = "\"SuscripcionId\"", nullable = false)
    private UUID suscripcionId;

    @Column(name = "\"PlanId\"", nullable = false)
    private UUID planId;

    @Column(name = "\"Monto\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "\"Moneda\"", nullable = false, length = 3)
    private String moneda;

    @Column(name = "\"InicioPeriodo\"", nullable = false)
    private Instant inicioPeriodo;

    @Column(name = "\"FinPeriodo\"", nullable = false)
    private Instant finPeriodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Estado\"", nullable = false, length = 20)
    private EstadoCobro estado;

    @Column(name = "\"PasarelaPago\"", nullable = false, length = 50)
    private String pasarelaPago;

    @Column(name = "\"TransaccionId\"", length = 255)
    private String transaccionId;

    @Column(name = "\"Intento\"", nullable = false)
    private Integer intento = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "\"DetallesRespuesta\"")
    private Map<String, Object> detallesRespuesta;
}
