package shopify.api.core.soporte;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MigracionesTest extends PruebaIntegracionBase {

    @Test
    void existenLasTablasDeV2() {
        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tablas).contains("PlanesSuscripcion", "SuscripcionesTienda", "CobrosSuscripcion",
                "TarifasEnvio", "MovimientosInventario", "RegistroAuditoria");
    }

    @Test
    void existenLosTresPlanes() {
        List<String> codigos = jdbc.queryForList("SELECT \"Codigo\" FROM \"PlanesSuscripcion\"", String.class);
        assertThat(codigos).containsExactlyInAnyOrder("BASICO", "ESTANDAR", "AVANZADO");
    }

    @Test
    void existenLosRolesDeTienda() {
        List<String> codigos = jdbc.queryForList("SELECT \"Codigo\" FROM \"Roles\"", String.class);
        assertThat(codigos).contains("PROPIETARIO", "ADMINISTRADOR", "GESTOR_CATALOGO", "GESTOR_PEDIDOS");
    }

    @Test
    void flywayAplicoLasVersiones1Y2() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true AND version IS NOT NULL", String.class);
        assertThat(versiones).contains("1", "2");
    }
}
