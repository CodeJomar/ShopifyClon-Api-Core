package shopify.api.core.comun.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila solo-añadir de {@code RegistroAuditoria} (AUD-002: no se edita ni se borra). */
@Getter
@Setter
@Entity
@Table(name = "\"RegistroAuditoria\"")
public class RegistroAuditoriaEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"")
    private UUID tiendaId;

    @Column(name = "\"UsuarioId\"")
    private UUID usuarioId;

    @Column(name = "\"Accion\"", nullable = false, length = 60)
    private String accion;

    @Column(name = "\"Entidad\"", nullable = false, length = 60)
    private String entidad;

    @Column(name = "\"EntidadId\"")
    private UUID entidadId;

    @Column(name = "\"Motivo\"")
    private String motivo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "\"Detalle\"")
    private Map<String, Object> detalle;

    @Column(name = "\"IdCorrelacion\"", length = 64)
    private String idCorrelacion;
}
