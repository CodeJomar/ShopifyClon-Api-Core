package shopify.api.core.modulos.balance.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

/** Asiento del libro de repartos: lo que le toca a una tienda de un pago, con la comisión de la plataforma. */
@Getter
@Setter
@Entity
@Table(name = "\"RepartosPago\"")
public class RepartoPagoEntidad extends AuditoriaEntidad {

    @Column(name = "\"PedidoId\"")
    private UUID pedidoId;

    @Column(name = "\"OrdenId\"", nullable = false)
    private UUID ordenId;

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    /** VENTA (REEMBOLSO llega con valores negativos más adelante). */
    @Column(name = "\"Tipo\"", nullable = false, length = 20)
    private String tipo = "VENTA";

    @Column(name = "\"Bruto\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal bruto;

    @Column(name = "\"Comision\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal comision;

    @Column(name = "\"Neto\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal neto;

    @Column(name = "\"Moneda\"", nullable = false, length = 3)
    private String moneda;
}
