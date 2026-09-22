package shopify.api.core.modules.usuario.infrastructure.persistencia.entidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.auditoria.AuditoriaEntidad;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "\"TokensSeguridad\"")
public class TokenSeguridadEntidad extends AuditoriaEntidad {

    @Column(name = "\"CodigoToken\"", nullable = false, length = 100)
    private String codigoToken;

    @Column(name = "\"Tipo\"", nullable = false, length = 30) // "ACTIVACION_CUENTA" o "RECUPERACION_CLAVE"
    private String tipo;

    @Column(name = "\"FechaExpiracion\"", nullable = false)
    private Instant fechaExpiracion;

    @Column(name = "\"Usado\"", nullable = false)
    private Boolean usado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"UsuarioId\"", nullable = false)
    private UsuarioEntidad usuario;
}