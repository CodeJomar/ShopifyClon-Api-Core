package shopify.api.core.modulos.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

@Getter
@Setter
@Entity
@Table(name = "\"TarifasEnvio\"")
public class TarifaEnvioEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    @Column(name = "\"Nombre\"", nullable = false, length = 100)
    private String nombre;

    @Column(name = "\"Descripcion\"", length = 255)
    private String descripcion;

    @Column(name = "\"Precio\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    /** NULL = nunca gratis por monto (ENV-002). */
    @Column(name = "\"MontoMinimoGratis\"", precision = 12, scale = 2)
    private BigDecimal montoMinimoGratis;

    @Column(name = "\"EstaActiva\"", nullable = false)
    private Boolean estaActiva = true;

    @Column(name = "\"Orden\"", nullable = false)
    private Integer orden = 0;
}
