package shopify.api.core.modulos.orden.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;

/** Orden de compra (tabla {@code Ordenes}). Los importes y las instantáneas se guardan tal como se calcularon. */
@Getter
@Setter
@Entity
@Table(name = "\"Ordenes\"")
public class OrdenEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    /** Opcional: una compra como invitado no tiene usuario. */
    @Column(name = "\"UsuarioId\"")
    private UUID usuarioId;

    /** Número legible y correlativo por tienda, como "#1001". */
    @Column(name = "\"NumeroOrden\"", nullable = false, length = 30)
    private String numeroOrden;

    @Column(name = "\"CorreoCliente\"", nullable = false, length = 255)
    private String correoCliente;

    @Column(name = "\"NombreCliente\"", nullable = false, length = 150)
    private String nombreCliente;

    @Column(name = "\"TelefonoCliente\"", length = 30)
    private String telefonoCliente;

    @Column(name = "\"Subtotal\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "\"DescuentoTotal\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoTotal;

    @Column(name = "\"CostoEnvio\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoEnvio;

    @Column(name = "\"ImpuestosTotal\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal impuestosTotal;

    @Column(name = "\"Total\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Estado\"", nullable = false, length = 20)
    private EstadoOrden estado = EstadoOrden.ABIERTA;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"EstadoPago\"", nullable = false, length = 30)
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"EstadoPreparacion\"", nullable = false, length = 30)
    private EstadoPreparacion estadoPreparacion = EstadoPreparacion.NO_PREPARADO;

    /** Instantánea del snapshot de envío y facturación (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "\"DireccionEnvio\"")
    private Map<String, Object> direccionEnvio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "\"DireccionFacturacion\"")
    private Map<String, Object> direccionFacturacion;

    @Column(name = "\"CuponId\"")
    private UUID cuponId;

    @Column(name = "\"CodigoCupon\"", length = 50)
    private String codigoCupon;

    @Column(name = "\"NombreTarifaEnvio\"", length = 100)
    private String nombreTarifaEnvio;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"PasarelaPago\"", length = 50)
    private PasarelaPago pasarelaPago;

    @Column(name = "\"TasaImpuesto\"", nullable = false, precision = 5, scale = 4)
    private BigDecimal tasaImpuesto;

    @Column(name = "\"PreciosIncluyenImpuesto\"", nullable = false)
    private Boolean preciosIncluyenImpuesto;

    /** Comisión de la plataforma; 0 al crear (se calcula al pagar, fuera de este cambio). */
    @Column(name = "\"ComisionPlataforma\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal comisionPlataforma = BigDecimal.ZERO;

    /** Clave que envió el cliente en {@code Idempotency-Key}; única por tienda (UxOrdenesClaveIdempotencia). */
    @Column(name = "\"ClaveIdempotencia\"")
    private UUID claveIdempotencia;

    /** Token de acceso del comprador a su orden (único). */
    @Column(name = "\"TokenAcceso\"", length = 100)
    private String tokenAcceso;

    @Column(name = "\"FechaExpiracionPago\"")
    private Instant fechaExpiracionPago;

    @Column(name = "\"FechaCancelacion\"")
    private Instant fechaCancelacion;

    @Column(name = "\"MotivoCancelacion\"", length = 30)
    private String motivoCancelacion;

    @Column(name = "\"DetalleCancelacion\"", length = 255)
    private String detalleCancelacion;

    @Column(name = "\"FechaCierre\"")
    private Instant fechaCierre;
}
