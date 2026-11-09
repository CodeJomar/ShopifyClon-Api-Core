package shopify.api.core.modulos.ruta.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.ruta.modelo.EstadoRuta;

/** Hoja de ruta de reparto: un chofer y una placa recorren los envíos de una tienda en un orden. */
@Getter
@Setter
@Entity
@Table(name = "\"Rutas\"")
public class RutaEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    /** Correlativo por tienda, por ejemplo {@code RT-0001}. */
    @Column(name = "\"Codigo\"", nullable = false, length = 20)
    private String codigo;

    @Column(name = "\"Fecha\"", nullable = false)
    private LocalDate fecha;

    @Column(name = "\"Chofer\"", nullable = false, length = 150)
    private String chofer;

    @Column(name = "\"Placa\"", nullable = false, length = 20)
    private String placa;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Estado\"", nullable = false, length = 20)
    private EstadoRuta estado = EstadoRuta.BORRADOR;

    @Column(name = "\"Nota\"", length = 500)
    private String nota;
}
