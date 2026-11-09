package shopify.api.core.modulos.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.envio.modelo.EstadoEventoEnvio;

/** Una fila del historial de seguimiento de un envío (tabla {@code EventosEnvio}); solo se añaden filas. */
@Getter
@Setter
@Entity
@Table(name = "\"EventosEnvio\"")
public class EventoEnvioEntidad extends AuditoriaEntidad {

    @Column(name = "\"EnvioId\"", nullable = false)
    private UUID envioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Estado\"", nullable = false, length = 50)
    private EstadoEventoEnvio estado;

    @Column(name = "\"Nota\"", length = 500)
    private String nota;

    /** Foto de entrega (https), solo con {@code ENTREGADO}. */
    @Column(name = "\"FotoUrl\"")
    private String fotoUrl;

    @Column(name = "\"FechaEvento\"", nullable = false)
    private Instant fechaEvento;
}
