package shopify.api.core.modulos.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shopify.api.core.modulos.suscripcion.programador.SuscripcionProgramador;

/** T-022, pendiente 1: un cobro PENDIENTE de más de una hora se rechaza con motivo; uno reciente no se toca. */
class CobroHuerfanoProgramadorTest extends PruebaPanelSuscripcionBase {

    @Autowired
    private SuscripcionProgramador programador;

    @Test
    void unCobroPendienteDeMasDeUnaHoraSeRechazaConSuMotivoYUnoRecienteNo() throws Exception {
        Sesion propietario = nuevaSesion();
        Tienda tienda = tiendaPorApi(propietario);
        UUID suscripcion = jdbc.queryForObject("SELECT \"Id\" FROM \"SuscripcionesTienda\" WHERE \"TiendaId\" = ? "
                + "AND \"Eliminado\" = FALSE AND \"Estado\" <> 'CANCELADA'", UUID.class, tienda.id());
        UUID plan = jdbc.queryForObject("SELECT \"PlanId\" FROM \"SuscripcionesTienda\" WHERE \"Id\" = ?", UUID.class, suscripcion);
        UUID viejo = cobroPendiente(suscripcion, plan, Instant.now().minus(2, ChronoUnit.HOURS));
        UUID reciente = cobroPendiente(suscripcion, plan, Instant.now().minus(5, ChronoUnit.MINUTES));

        programador.ejecutar();

        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"CobrosSuscripcion\" WHERE \"Id\" = ?", String.class, viejo))
                .isEqualTo("RECHAZADO");
        assertThat(jdbc.queryForObject("SELECT \"DetallesRespuesta\" ->> 'motivo' FROM \"CobrosSuscripcion\" WHERE \"Id\" = ?",
                String.class, viejo)).isEqualTo("COBRO_HUERFANO");
        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"CobrosSuscripcion\" WHERE \"Id\" = ?", String.class, reciente))
                .isEqualTo("PENDIENTE");
    }

    private UUID cobroPendiente(UUID suscripcion, UUID plan, Instant creado) {
        Instant inicio = creado.minus(31, ChronoUnit.DAYS);
        return jdbc.queryForObject("INSERT INTO \"CobrosSuscripcion\" (\"SuscripcionId\", \"PlanId\", \"Monto\", \"Moneda\", "
                + "\"InicioPeriodo\", \"FinPeriodo\", \"Estado\", \"PasarelaPago\", \"Intento\", \"FechaCreacion\") "
                + "VALUES (?, ?, 29.00, 'USD', ?, ?, 'PENDIENTE', 'SIMULADA', 1, ?) RETURNING \"Id\"", UUID.class,
                suscripcion, plan, Timestamp.from(inicio), Timestamp.from(creado), Timestamp.from(creado));
    }
}
