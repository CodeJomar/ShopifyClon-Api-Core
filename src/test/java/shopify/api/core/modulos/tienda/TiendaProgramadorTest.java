package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shopify.api.core.modulos.tienda.programador.TiendaProgramador;

/** SUS-007: una tienda vencida hace más de 30 días se desactiva con auditoría; hace 29 sigue activa. */
class TiendaProgramadorTest extends PruebaTiendaBase {

    @Autowired
    private TiendaProgramador programador;

    private void vencidaDesde(UUID tiendaId, int diasAtras) {
        Instant vencimiento = Instant.now().minus(diasAtras, ChronoUnit.DAYS);
        jdbc.update("UPDATE \"SuscripcionesTienda\" SET \"FechaVencimiento\" = ? WHERE \"TiendaId\" = ?",
                Timestamp.from(vencimiento), tiendaId);
    }

    private boolean estaActiva(UUID tiendaId) {
        return jdbc.queryForObject("SELECT \"EstaActiva\" FROM \"Tiendas\" WHERE \"Id\" = ?", Boolean.class, tiendaId);
    }

    @Test
    void tiendaVencidaHace31DiasSeDesactivaYAuditaYLaDe29SigueActiva() throws Exception {
        Sesion propietario = nuevaSesion();
        crearTiendaPorApi(propietario, subdominioUnico());
        UUID hace31 = crearTiendaPorApi(propietario, subdominioUnico());
        UUID hace29 = crearTiendaPorApi(propietario, subdominioUnico());
        vencidaDesde(hace31, 31);
        vencidaDesde(hace29, 29);

        programador.ejecutar();

        assertThat(estaActiva(hace31)).isFalse();
        assertThat(contarAuditoria(hace31, "TIENDA_DESACTIVADA")).isEqualTo(1);
        assertThat(estaActiva(hace29)).isTrue();
        assertThat(contarAuditoria(hace29, "TIENDA_DESACTIVADA")).isZero();

        programador.ejecutar();
        assertThat(contarAuditoria(hace31, "TIENDA_DESACTIVADA")).isEqualTo(1);
    }
}
