package shopify.api.core.modulos.ruta.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

/** Un envío dentro de una ruta, con su orden de visita. Sin {@code @ManyToOne}: referencias por id. */
@Getter
@Setter
@Entity
@Table(name = "\"ParadasRuta\"")
public class ParadaRutaEntidad extends AuditoriaEntidad {

    @Column(name = "\"RutaId\"", nullable = false)
    private UUID rutaId;

    @Column(name = "\"EnvioId\"", nullable = false)
    private UUID envioId;

    @Column(name = "\"Posicion\"", nullable = false)
    private Integer posicion;
}
