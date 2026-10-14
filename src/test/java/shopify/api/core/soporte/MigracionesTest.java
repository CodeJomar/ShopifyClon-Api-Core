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
    void flywayAplicoLasVersiones1A3() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true AND version IS NOT NULL", String.class);
        assertThat(versiones).contains("1", "2", "3");
    }

    @Test
    void v3RetiraLosRolesClienteYAdminTienda() {
        // En una base limpia esos roles no existen; en Supabase existían. En ambos casos no debe quedar ninguno activo.
        Integer activos = jdbc.queryForObject(
                "SELECT count(*) FROM \"Roles\" WHERE \"Codigo\" IN ('CLIENTE', 'ADMIN_TIENDA') AND \"Eliminado\" = FALSE",
                Integer.class);
        assertThat(activos).isZero();
    }

    @Test
    void v3AgregaIntentosFallidosATokens() {
        Integer columnas = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 'TokensSeguridad' AND column_name = 'IntentosFallidos'", Integer.class);
        assertThat(columnas).isEqualTo(1);
    }
}
