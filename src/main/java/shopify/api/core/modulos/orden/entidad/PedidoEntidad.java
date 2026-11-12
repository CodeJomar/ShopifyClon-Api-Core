package shopify.api.core.modulos.orden.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;

/** Compra del comprador (tabla {@code Pedidos}): agrupa una orden hija por tienda. Su estado se deriva de las hijas. */
@Getter
@Setter
@Entity
@Table(name = "\"Pedidos\"")
public class PedidoEntidad extends AuditoriaEntidad {

    @Column(name = "\"UsuarioId\"")
    private UUID usuarioId;

    /** Número legible y global, como "P-000123". */
    @Column(name = "\"NumeroPedido\"", nullable = false, length = 30)
    private String numeroPedido;

    @Column(name = "\"CorreoCliente\"", nullable = false, length = 255)
    private String correoCliente;

    @Column(name = "\"NombreCliente\"", nullable = false, length = 150)
    private String nombreCliente;

    @Column(name = "\"TelefonoCliente\"", length = 30)
    private String telefonoCliente;

    @Column(name = "\"Moneda\"", nullable = false, length = 3)
    private String moneda;

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
    @Column(name = "\"PasarelaPago\"", length = 50)
    private PasarelaPago pasarelaPago;

    /** Clave que envió el cliente en {@code Idempotency-Key} del checkout global; única. */
    @Column(name = "\"ClaveIdempotencia\"")
    private UUID claveIdempotencia;

    @Column(name = "\"TokenAcceso\"", nullable = false, length = 100)
    private String tokenAcceso;

    @Column(name = "\"FechaExpiracionPago\"")
    private Instant fechaExpiracionPago;
}
