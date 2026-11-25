package shopify.api.core.modulos.respaldo.servicio;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;

/**
 * Respaldo de la base de datos (R-023): exporta todas las tablas de {@code public} a un archivo {@code .zip} con una
 * instantánea consistente (una sola transacción de solo lectura), más un manifiesto y un script de restauración. No usa
 * {@code pg_dump}: usa COPY del driver, así que no depende de binarios ni de la versión del servidor. El esquema no se
 * respalda: lo recrea Flyway al arrancar la API sobre una base vacía, y el script de restauración carga los datos.
 * Guarda los últimos {@code app.respaldo.retencion-dias} días. Un candado de PostgreSQL impide dos respaldos a la vez
 * aunque haya varias instancias.
 */
@Slf4j
@Service
public class RespaldoServicio {

    static final String PREFIJO = "respaldo-";
    static final String EXTENSION = ".zip";
    private static final long CLAVE_CANDADO = 7_341_220_001L;
    private static final DateTimeFormatter FORMATO_NOMBRE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Resultado de un respaldo (también el último intento, sea cual sea su desenlace). */
    public record Resultado(String nombre, long bytes, int tablas, long filas, long duracionMs, Instant creadoEn,
                            boolean exito, String error) {
    }

    public record Archivo(String nombre, long bytes, Instant fecha) {
    }

    private final DataSource dataSource;
    private final ObjectMapper json;
    private final Clock reloj;
    private final Path directorio;
    private final int retencionDias;
    private final ZoneId zona;
    private final AtomicReference<Resultado> ultimo = new AtomicReference<>();

    public RespaldoServicio(DataSource dataSource, ObjectMapper json, Clock reloj,
                            @Value("${app.respaldo.directorio:respaldos}") String directorio,
                            @Value("${app.respaldo.retencion-dias:7}") int retencionDias,
                            @Value("${app.zona-horaria:America/Lima}") String zona) {
        this.dataSource = dataSource;
        this.json = json;
        this.reloj = reloj;
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        this.retencionDias = Math.max(retencionDias, 1);
        this.zona = ZoneId.of(zona);
    }

    public Path directorio() {
        return directorio;
    }

    public int retencionDias() {
        return retencionDias;
    }

    public ZoneId zona() {
        return zona;
    }

    public Resultado ultimo() {
        return ultimo.get();
    }

    /** Crea un respaldo ahora. Si falla, deja el error en {@link #ultimo()} y lo relanza. */
    public Resultado crear() {
        Instant inicio = reloj.instant();
        String nombre = PREFIJO + FORMATO_NOMBRE.format(inicio.atZone(zona)) + EXTENSION;
        Path parcial = directorio.resolve(nombre + ".parcial");
        try {
            Files.createDirectories(directorio);
            Resultado resultado;
            try (Connection conexion = dataSource.getConnection()) {
                if (!tomarCandado(conexion)) {
                    throw new ConflictoEstadoException("RES-901", "Ya hay un respaldo en curso");
                }
                try {
                    resultado = escribir(conexion, parcial, nombre, inicio);
                } finally {
                    soltarCandado(conexion);
                }
            }
            Files.move(parcial, directorio.resolve(nombre), StandardCopyOption.ATOMIC_MOVE);
            Path finalizado = directorio.resolve(nombre);
            soloElDuenio(finalizado);
            Resultado hecho = new Resultado(nombre, Files.size(finalizado), resultado.tablas(), resultado.filas(),
                    Duration.between(inicio, reloj.instant()).toMillis(), inicio, true, null);
            ultimo.set(hecho);
            log.info("Respaldo {} listo: {} tablas, {} filas, {} bytes, {} ms", nombre, hecho.tablas(), hecho.filas(),
                    hecho.bytes(), hecho.duracionMs());
            limpiarAntiguos();
            return hecho;
        } catch (ConflictoEstadoException ex) {
            throw ex;
        } catch (IOException | SQLException | RuntimeException ex) {
            borrarSilencioso(parcial);
            ultimo.set(new Resultado(nombre, 0, 0, 0, Duration.between(inicio, reloj.instant()).toMillis(), inicio, false,
                    ex.getClass().getSimpleName()));
            log.error("El respaldo {} falló ({}); revisa el espacio en disco y la conexión a la base", nombre,
                    ex.getClass().getSimpleName());
            throw new IllegalStateException("No se pudo crear el respaldo", ex);
        }
    }

    /** Respaldos guardados, del más reciente al más antiguo. */
    public List<Archivo> listar() {
        if (!Files.isDirectory(directorio)) {
            return List.of();
        }
        try (Stream<Path> archivos = Files.list(directorio)) {
            return archivos.filter(p -> esRespaldo(p.getFileName().toString()))
                    .map(p -> {
                        try {
                            return new Archivo(p.getFileName().toString(), Files.size(p), Files.getLastModifiedTime(p).toInstant());
                        } catch (IOException ex) {
                            return null;
                        }
                    })
                    .filter(a -> a != null)
                    .sorted(Comparator.comparing(Archivo::nombre).reversed())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------ interno

    private Resultado escribir(Connection conexion, Path destino, String nombre, Instant inicio) throws IOException, SQLException {
        conexion.setAutoCommit(false);
        conexion.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        conexion.setReadOnly(true);
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SELECT 1"); // fija la instantánea desde aquí
        }
        List<String> tablas = tablas(conexion);
        Map<String, Long> filasPorTabla = new LinkedHashMap<>();
        PGConnection pg = conexion.unwrap(PGConnection.class);
        long total = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destino))) {
            for (String tabla : tablas) {
                zip.putNextEntry(new ZipEntry("datos/" + tabla + ".csv"));
                long filas = pg.getCopyAPI().copyOut("COPY \"public\".\"" + tabla.replace("\"", "\"\"")
                        + "\" TO STDOUT WITH (FORMAT csv, HEADER true)", new NoCerrar(zip));
                zip.closeEntry();
                filasPorTabla.put(tabla, filas);
                total += filas;
            }
            Map<String, Object> manifiesto = new LinkedHashMap<>();
            manifiesto.put("creado_en", inicio.toString());
            manifiesto.put("zona", zona.getId());
            manifiesto.put("version_esquema", versionEsquema(conexion));
            manifiesto.put("tablas", filasPorTabla);
            agregar(zip, "manifiesto.json", json.writerWithDefaultPrettyPrinter().writeValueAsString(manifiesto));
            agregar(zip, "restaurar.psql", guion(tablas, secuencias(conexion)));
            agregar(zip, "LEEME.txt", instrucciones(nombre));
        } finally {
            conexion.rollback();
        }
        return new Resultado(nombre, 0, tablas.size(), total, 0, inicio, true, null);
    }

    private static List<String> tablas(Connection conexion) throws SQLException {
        List<String> tablas = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tablas.add(rs.getString(1));
            }
        }
        return tablas;
    }

    private static String versionEsquema(Connection conexion) {
        try (Statement s = conexion.createStatement();
             ResultSet rs = s.executeQuery("SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException ex) {
            return null;
        }
    }

    private static Map<String, Long> secuencias(Connection conexion) throws SQLException {
        Map<String, Long> secuencias = new LinkedHashMap<>();
        try (Statement s = conexion.createStatement();
             ResultSet rs = s.executeQuery("SELECT sequencename, last_value FROM pg_sequences WHERE schemaname = 'public' "
                     + "AND last_value IS NOT NULL ORDER BY sequencename")) {
            while (rs.next()) {
                secuencias.put(rs.getString(1), rs.getLong(2));
            }
        }
        return secuencias;
    }

    /** Script psql que carga los datos en una base con el esquema ya creado (la API lo crea con Flyway al arrancar). */
    private static String guion(List<String> tablas, Map<String, Long> secuencias) {
        StringBuilder s = new StringBuilder();
        s.append("-- Restauración de datos. Ejecutar desde la carpeta donde se descomprimió el respaldo:\n")
                .append("--   psql \"$DATABASE_URL\" -f restaurar.psql\n")
                .append("-- La base destino debe tener el esquema (arranca la API una vez con Flyway) y NINGÚN dato que quieras conservar.\n")
                .append("\\set ON_ERROR_STOP on\nBEGIN;\nSET session_replication_role = replica;\n");
        List<String> restaurables = tablas.stream().filter(t -> !t.equals("flyway_schema_history")).toList();
        if (!restaurables.isEmpty()) {
            s.append("TRUNCATE ").append(String.join(", ", restaurables.stream().map(t -> "\"" + t + "\"").toList())).append(" CASCADE;\n");
        }
        for (String tabla : restaurables) {
            s.append("\\copy \"").append(tabla).append("\" FROM 'datos/").append(tabla).append(".csv' WITH (FORMAT csv, HEADER true)\n");
        }
        secuencias.forEach((nombre, valor) -> s.append("SELECT setval('\"").append(nombre).append("\"', ").append(valor).append(");\n"));
        s.append("COMMIT;\n");
        return s.toString();
    }

    private static String instrucciones(String nombre) {
        return "Respaldo " + nombre + "\n\n"
                + "Contiene los DATOS de todas las tablas (datos/*.csv), manifiesto.json con las filas por tabla y la versión del esquema,\n"
                + "y restaurar.psql. El esquema no se respalda: lo crea Flyway al arrancar la API sobre una base vacía.\n\n"
                + "Para restaurar:\n"
                + "  1. Crea una base vacía y arranca la API una vez apuntando a ella (Flyway crea el esquema); luego detenla.\n"
                + "  2. Descomprime este archivo y, desde esa carpeta: psql \"$DATABASE_URL\" -f restaurar.psql\n"
                + "  3. Arranca la API y verifica el conteo de filas contra manifiesto.json.\n\n"
                + "Este archivo contiene TODOS los datos (incluidos hashes de contraseñas y datos personales): guárdalo como un secreto.\n";
    }

    private static void agregar(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static boolean tomarCandado(Connection conexion) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, CLAVE_CANDADO);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void soltarCandado(Connection conexion) {
        try {
            conexion.rollback();
            conexion.setReadOnly(false);
            conexion.setAutoCommit(true);
            try (PreparedStatement ps = conexion.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                ps.setLong(1, CLAVE_CANDADO);
                ps.execute();
            }
        } catch (SQLException ex) {
            log.warn("No se pudo soltar el candado del respaldo; se libera al cerrar la conexión");
        }
    }

    /** Borra los respaldos con más de N días, pero nunca el más reciente. */
    void limpiarAntiguos() {
        List<Archivo> archivos = listar();
        Instant limite = reloj.instant().minus(Duration.ofDays(retencionDias));
        for (int i = 1; i < archivos.size(); i++) {
            Archivo archivo = archivos.get(i);
            if (archivo.fecha().isBefore(limite)) {
                borrarSilencioso(directorio.resolve(archivo.nombre()));
                log.info("Respaldo antiguo eliminado: {}", archivo.nombre());
            }
        }
    }

    static boolean esRespaldo(String nombre) {
        return nombre.startsWith(PREFIJO) && nombre.endsWith(EXTENSION);
    }

    private static void soloElDuenio(Path archivo) {
        try {
            Files.setPosixFilePermissions(archivo, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ex) {
            // Sistema de archivos sin permisos POSIX (por ejemplo Windows): no hay nada que ajustar
        }
    }

    private static void borrarSilencioso(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ex) {
            log.warn("No se pudo borrar {}", archivo.getFileName());
        }
    }

    /** Evita que copyOut cierre el zip al terminar cada tabla. */
    private static final class NoCerrar extends OutputStream {
        private final OutputStream destino;

        NoCerrar(OutputStream destino) {
            this.destino = destino;
        }

        @Override
        public void write(int b) throws IOException {
            destino.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            destino.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            destino.flush();
        }

        @Override
        public void close() {
            // lo cierra quien lo abrió
        }
    }
}
