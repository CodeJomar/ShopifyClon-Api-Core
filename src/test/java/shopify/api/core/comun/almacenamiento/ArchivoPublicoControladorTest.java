package shopify.api.core.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** El endpoint público solo sirve el formato exacto que genera la subida y nunca llega al bucket con otra ruta. */
class ArchivoPublicoControladorTest {

    private static final String TIENDA = "3f2b8c1e-0a4d-4f6e-9b7a-1c2d3e4f5a6b";
    private static final String ARCHIVO = "0b9f6d2e-5c3a-4e8f-a1b2-c3d4e5f60718.png";

    private final List<String> pedidos = new ArrayList<>();
    private final AlmacenamientoImagenes falso = new AlmacenamientoImagenes() {
        @Override
        public String subir(UUID tiendaId, byte[] contenido, String tipo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ArchivoAlmacenado> leer(String ruta) {
            pedidos.add(ruta);
            return Optional.of(new ArchivoAlmacenado(new byte[]{1, 2, 3}, "image/png"));
        }
    };
    private final ArchivoPublicoControlador controlador = new ArchivoPublicoControlador(falso);

    @Test
    void sirveElArchivoConSuTipoYCacheLargaPublica() {
        ResponseEntity<byte[]> r = controlador.servir(TIENDA, ARCHIVO);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(r.getHeaders().getCacheControl()).contains("immutable").contains("public");
        assertThat(r.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(pedidos).containsExactly(TIENDA + "/" + ARCHIVO);
    }

    @Test
    void rechazaRutasQueNoTienenElFormatoDeLaSubidaSinTocarElBucket() {
        for (String archivo : List.of("../secreto.png", "foto.png", ARCHIVO + ".php", ARCHIVO.replace("png", "exe"), "..%2Fx.png")) {
            assertThat(controlador.servir(TIENDA, archivo).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(controlador.servir("no-es-uuid", ARCHIVO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controlador.servir("../../etc", ARCHIVO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(pedidos).isEmpty();
    }

    @Test
    void devuelve404SiElProveedorNoTieneElArchivo() {
        ArchivoPublicoControlador sinNada = new ArchivoPublicoControlador(new AlmacenamientoImagenes() {
            @Override
            public String subir(UUID tiendaId, byte[] contenido, String tipo) {
                return "";
            }
        });

        assertThat(sinNada.servir(TIENDA, ARCHIVO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
