package shopify.api.core.modulos.tienda.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

@Getter
@Setter
@Entity
@Table(name = "\"Tiendas\"")
public class TiendaEntidad extends AuditoriaEntidad {

    public static final int PRIMER_NUMERO_ORDEN = 1001;

    @Column(name = "\"PropietarioId\"", nullable = false)
    private UUID propietarioId;

    @Column(name = "\"Nombre\"", nullable = false, length = 100)
    private String nombre;

    @Column(name = "\"Subdominio\"", nullable = false, length = 100)
    private String subdominio;

    @Column(name = "\"DominioPersonalizado\"", length = 150)
    private String dominioPersonalizado;

    @Column(name = "\"Moneda\"", nullable = false, length = 3)
    private String moneda = "USD";

    @Column(name = "\"CorreoContacto\"", nullable = false, length = 255)
    private String correoContacto;

    /** Apariencia de la vitrina (R-031): códigos de lista blanca con valor por defecto. */
    @Column(name = "\"Paleta\"", nullable = false, length = 20)
    private String paleta = "OTEMU";

    @Column(name = "\"Tipografia\"", nullable = false, length = 20)
    private String tipografia = "MODERNA";

    @Column(name = "\"TamanoLetra\"", nullable = false, length = 20)
    private String tamanoLetra = "NORMAL";

    @Column(name = "\"Diseno\"", nullable = false, length = 20)
    private String diseno = "CUADRICULA";

    @Column(name = "\"Modo\"", nullable = false, length = 20)
    private String modo = "OSCURO";

    /** RUC de 11 dígitos, único entre tiendas (R-030). Nulo solo en tiendas anteriores a su introducción. */
    @Column(name = "\"Ruc\"", length = 11)
    private String ruc;

    @Column(name = "\"EstaActiva\"", nullable = false)
    private Boolean estaActiva = true;

    @Column(name = "\"TasaImpuesto\"", nullable = false, precision = 5, scale = 4)
    private BigDecimal tasaImpuesto = new BigDecimal("0.1800");

    @Column(name = "\"PreciosIncluyenImpuesto\"", nullable = false)
    private Boolean preciosIncluyenImpuesto = true;

    @Column(name = "\"ZonaHoraria\"", nullable = false, length = 50)
    private String zonaHoraria = "America/Lima";

    /** R-001: texto corto para la ficha pública de la tienda en el mercado. */
    @Column(name = "\"Descripcion\"", length = 500)
    private String descripcion;

    /** R-001: logo (https). */
    @Column(name = "\"UrlLogo\"", length = 2000)
    private String urlLogo;

    /** R-001: si es false la tienda no aparece en el mercado (su vitrina propia sigue funcionando). */
    @Column(name = "\"VisibleEnMercado\"", nullable = false)
    private Boolean visibleEnMercado = true;

    /** Solo el checkout lo cambia, con un UPDATE relativo (TIE-007); por eso no es actualizable desde JPA. */
    @Column(name = "\"SiguienteNumeroOrden\"", nullable = false, insertable = true, updatable = false)
    private Integer siguienteNumeroOrden = PRIMER_NUMERO_ORDEN;
}
