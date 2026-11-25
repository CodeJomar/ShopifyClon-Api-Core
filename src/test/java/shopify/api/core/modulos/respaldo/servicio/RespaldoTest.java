package shopify.api.core.modulos.respaldo.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import shopify.api.core.modulos.respaldo.servicio.RespaldoServicio.Resultado;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-023: respaldo de la base, retención, programación por hora y permisos. */
class RespaldoTest extends PruebaIntegracionBase {

    @Autowired
    private RespaldoServicio respaldos;

    private String leer(ZipFile zip, String entrada) throws Exception {
        ZipEntry e = zip.getEntry(entrada);
        assertThat(e).as("entrada " + entrada).isNotNull();
        return new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void creaUnZipConTodasLasTablasElManifiestoYElGuionDeRestauracion() throws Exception {
        crearUsuarioActivo(false);
        long usuarios = jdbc.queryForObject("SELECT count(*) FROM \"Usuarios\"", Long.class);

        Resultado resultado = respaldos.crear();

        assertThat(resultado.exito()).isTrue();
        assertThat(resultado.bytes()).isPositive();
        Path archivo = respaldos.directorio().resolve(resultado.nombre());
        assertThat(archivo).exists();
        assertThat(respaldos.listar()).extracting(RespaldoServicio.Archivo::nombre).contains(resultado.nombre());
        assertThat(respaldos.ultimo().nombre()).isEqualTo(resultado.nombre());

        try (ZipFile zip = new ZipFile(archivo.toFile())) {
            JsonNode manifiesto = json.readTree(leer(zip, "manifiesto.json"));
            assertThat(manifiesto.get("tablas").get("Usuarios").asLong()).isEqualTo(usuarios);
            assertThat(manifiesto.get("version_esquema").asInt()).isGreaterThanOrEqualTo(11);
            assertThat(manifiesto.get("tablas").has("flyway_schema_history")).isTrue();
            assertThat(resultado.tablas()).isEqualTo(manifiesto.get("tablas").size());

            String csv = leer(zip, "datos/Usuarios.csv");
            assertThat(csv.lines().findFirst().orElse("")).contains("CorreoElectronico");
            assertThat(csv.lines().count()).isEqualTo(usuarios + 1);

            String guion = leer(zip, "restaurar.psql");
            assertThat(guion).contains("\\copy \"Usuarios\" FROM 'datos/Usuarios.csv'", "session_replication_role = replica",
                    "TRUNCATE ");
            assertThat(guion).doesNotContain("\\copy \"flyway_schema_history\"");
            assertThat(leer(zip, "LEEME.txt")).contains("restaurar.psql");
        }
        assertThat(Files.list(respaldos.directorio()).filter(p -> p.toString().endsWith(".parcial"))).isEmpty();
    }

    @Test
    void laRetencionBorraLosAntiguosPeroNuncaElMasReciente() throws Exception {
        Files.createDirectories(respaldos.directorio());
        try (java.util.stream.Stream<Path> existentes = Files.list(respaldos.directorio())) {
            for (Path p : existentes.filter(x -> RespaldoServicio.esRespaldo(x.getFileName().toString())).toList()) {
                Files.delete(p);
            }
        }
        Path viejo = respaldos.directorio().resolve("respaldo-20200101-030000.zip");
        Path viejo2 = respaldos.directorio().resolve("respaldo-20200102-030000.zip");
        Path ajeno = respaldos.directorio().resolve("otro-archivo.txt");
        for (Path p : List.of(viejo, viejo2, ajeno)) {
            Files.writeString(p, "x");
            Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.from(Instant.now().minus(400, ChronoUnit.DAYS)));
        }

        respaldos.limpiarAntiguos();

        // El más reciente por nombre se conserva aunque sea viejo; el otro viejo se borra; lo ajeno no se toca
        assertThat(viejo2).exists();
        assertThat(viejo).doesNotExist();
        assertThat(ajeno).exists();
        Files.deleteIfExists(viejo2);
        Files.deleteIfExists(ajeno);
    }

    @Test
    void lasHorasSeLeenEnFormatoHHmmYUnaMalaHaceFallarElArranque() {
        assertThat(RespaldoProgramador.parsear("03:00")).containsExactly(LocalTime.of(3, 0));
        assertThat(RespaldoProgramador.parsear("3:05")).containsExactly(LocalTime.of(3, 5));
        assertThat(RespaldoProgramador.parsear("15:30, 03:00 ,03:00")).containsExactly(LocalTime.of(3, 0), LocalTime.of(15, 30));
        assertThatThrownBy(() -> RespaldoProgramador.parsear("25:00")).isInstanceOf(IllegalStateException.class).hasMessageContaining("HH:mm");
        assertThatThrownBy(() -> RespaldoProgramador.parsear("tres de la mañana")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RespaldoProgramador.parsear("")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soloQuienTienePermisoDeSeguridadVeYCreaRespaldos() throws Exception {
        UUID admin = crearUsuarioActivo(true);
        String jwtAdmin = tokenPara(admin, jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, admin));

        JsonNode estado = cuerpo(getConToken("/plataforma/respaldos", jwtAdmin, null).andExpect(status().isOk())).get("datos");
        assertThat(estado.get("habilitado").asBoolean()).isFalse();
        assertThat(estado.get("retencion_dias").asInt()).isEqualTo(7);
        assertThat(estado.get("archivos").isArray()).isTrue();

        mvc.perform(post("/plataforma/respaldos").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAdmin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.datos.exito").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"Accion\" = 'SEGURIDAD_RESPALDO_MANUAL'",
                Integer.class)).isGreaterThanOrEqualTo(1);

        // Soporte administra la plataforma pero no tiene el permiso de seguridad
        UUID soporte = crearUsuarioActivo(false);
        jdbc.update("UPDATE \"Usuarios\" SET \"RolPlataformaId\" = (SELECT \"Id\" FROM \"RolesPlataforma\" WHERE \"Codigo\" = 'SOPORTE') WHERE \"Id\" = ?", soporte);
        String jwtSoporte = tokenPara(soporte, jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, soporte));
        getConToken("/plataforma/respaldos", jwtSoporte, null).andExpect(status().isForbidden());

        UUID comun = crearUsuarioActivo(false);
        String jwtComun = tokenPara(comun, jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, comun));
        getConToken("/plataforma/respaldos", jwtComun, null).andExpect(status().isNotFound());
        getConToken("/plataforma/respaldos", null, null).andExpect(status().isUnauthorized());
    }
}
