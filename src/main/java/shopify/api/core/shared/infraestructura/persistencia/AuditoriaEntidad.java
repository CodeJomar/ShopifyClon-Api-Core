package shopify.api.core.shared.infraestructura.persistencia;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditoriaEntidad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "\"Id\"", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "\"FechaCreacion\"", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @CreatedBy
    @Column(name = "\"UsuarioCreacion\"", updatable = false)
    private UUID usuarioCreacion;

    @LastModifiedDate
    @Column(name = "\"FechaEdicion\"")
    private Instant fechaEdicion;

    @LastModifiedBy
    @Column(name = "\"UsuarioEdicion\"")
    private UUID usuarioEdicion;

    @Column(name = "\"Eliminado\"", nullable = false)
    private Boolean eliminado = false;
}