package shopify.api.core.modulos.usuario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.usuario.modelo.TipoCodigoSeguridad;

@Getter
@Setter
@Entity
@Table(name = "\"TokensSeguridad\"")
public class TokenSeguridadEntidad extends AuditoriaEntidad {

    @Column(name = "\"UsuarioId\"", nullable = false)
    private UUID usuarioId;

    /** SHA-256 hex del código; nunca el código en claro (USR-007). */
    @Column(name = "\"CodigoToken\"", nullable = false, length = 100)
    private String codigoToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Tipo\"", nullable = false, length = 30)
    private TipoCodigoSeguridad tipo;

    @Column(name = "\"FechaExpiracion\"", nullable = false)
    private Instant fechaExpiracion;

    @Column(name = "\"Usado\"", nullable = false)
    private Boolean usado = false;

    @Column(name = "\"IntentosFallidos\"", nullable = false)
    private Integer intentosFallidos = 0;
}
