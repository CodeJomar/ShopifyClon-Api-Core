package shopify.api.core.modulos.suscripcion.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.suscripcion.modelo.CicloFacturacion;
import shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion;

@Getter
@Setter
@Entity
@Table(name = "\"SuscripcionesTienda\"")
public class SuscripcionTiendaEntidad extends AuditoriaEntidad {

    /** Sin {@code @ManyToOne}: las referencias entre módulos son por id (arquitectura-backend.md). */
    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    @Column(name = "\"PlanId\"", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Estado\"", nullable = false, length = 20)
    private EstadoSuscripcion estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"EstadoAnterior\"", length = 20)
    private EstadoSuscripcion estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"CicloFacturacion\"", nullable = false, length = 10)
    private CicloFacturacion cicloFacturacion = CicloFacturacion.MENSUAL;

    /** Congelado por período (SUS-003). */
    @Column(name = "\"PrecioPactado\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioPactado;

    @Column(name = "\"Moneda\"", nullable = false, length = 3)
    private String moneda;

    @Column(name = "\"FechaInicio\"", nullable = false)
    private Instant fechaInicio;

    @Column(name = "\"FechaFinPrueba\"")
    private Instant fechaFinPrueba;

    @Column(name = "\"InicioPeriodoActual\"", nullable = false)
    private Instant inicioPeriodoActual;

    @Column(name = "\"FinPeriodoActual\"", nullable = false)
    private Instant finPeriodoActual;

    @Column(name = "\"FechaVencimiento\"")
    private Instant fechaVencimiento;

    @Column(name = "\"CancelarAlFinalizarPeriodo\"", nullable = false)
    private Boolean cancelarAlFinalizarPeriodo = false;

    @Column(name = "\"FechaCancelacion\"")
    private Instant fechaCancelacion;

    @Column(name = "\"MotivoEstado\"", length = 255)
    private String motivoEstado;

    @Column(name = "\"ReferenciaPasarela\"", length = 255)
    private String referenciaPasarela;
}
