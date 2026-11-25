package shopify.api.core.comun.almacenamiento;

import io.swagger.v3.oas.annotations.Hidden;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sirve las imágenes guardadas en un bucket privado ({@code GET /archivos/<tiendaId>/<archivo>}), públicas y con caché
 * larga: cada archivo lleva un nombre único y nunca cambia. Solo acepta el formato exacto que genera la subida
 * (UUID + extensión de imagen), así no se puede pedir otra cosa del bucket.
 */
@Hidden
@RestController
@RequestMapping("/archivos")
@RequiredArgsConstructor
public class ArchivoPublicoControlador {

    private static final Pattern ARCHIVO = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private final AlmacenamientoImagenes almacenamiento;

    @GetMapping("/{tiendaId}/{archivo}")
    public ResponseEntity<byte[]> servir(@PathVariable String tiendaId, @PathVariable String archivo) {
        if (!esUuid(tiendaId) || !ARCHIVO.matcher(archivo).matches()) {
            return ResponseEntity.notFound().build();
        }
        return almacenamiento.leer(tiendaId + "/" + archivo)
                .map(a -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(a.tipo() == null ? "application/octet-stream" : a.tipo()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(a.contenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static boolean esUuid(String texto) {
        try {
            return UUID.fromString(texto).toString().equals(texto.toLowerCase());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
