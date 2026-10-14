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
import shopify.api.core.modulos.envio.modelo.EstadoEnvio;

/** Envío físico de una orden (tabla {@code Envios}). Uno no eliminado por orden (UxEnviosOrden). */
@Getter
@Setter
@Entity
@Table(name = "\"Envios\"")
public class EnvioEntidad extends AuditoriaEntidad {

    @Column(name = "\"OrdenId\"", nullable = false)
    private UUID ordenId;

    @Column(name = "\"EmpresaTransporte\"", length = 100)
    private String empresaTransporte;

    @Column(name = "\"NumeroGuia\"", length = 100)
    private String numeroGuia;

    @Column(name = "\"UrlSeguimiento\"")
    private String urlSeguimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Estado\"", nullable = false, length = 50)
    private EstadoEnvio estado = EstadoEnvio.CREADO;

    @Column(name = "\"FechaDespacho\"")
    private Instant fechaDespacho;

    @Column(name = "\"FechaEntregaEstimada\"")
    private Instant fechaEntregaEstimada;

    @Column(name = "\"FechaEntregaReal\"")
    private Instant fechaEntregaReal;
}
