package shopify.api.core.modulos.suscripcion.modelo;

import java.time.Instant;
import java.util.UUID;

/** Proyección de solo lectura del listado de la plataforma (alias de la consulta nativa). */
public interface SuscripcionPlataformaVista {

    UUID getTiendaId();

    String getSubdominio();

    String getCodigoPlan();

    String getEstado();

    Instant getFinPeriodoActual();
}
