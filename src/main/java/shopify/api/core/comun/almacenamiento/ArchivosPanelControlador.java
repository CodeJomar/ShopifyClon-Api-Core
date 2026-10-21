package shopify.api.core.comun.almacenamiento;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;

/**
 * Endpoint para subir imágenes al almacenamiento.
 * <p>
 * Requiere {@code X-Tienda-Id} y permiso {@code CATALOGO_GESTIONAR}
 * (misma protección que los endpoints del panel de catálogo).
 */
@Tag(name = "Panel: archivos", description = "Subida de archivos (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/archivos")
@RequiredArgsConstructor
public class ArchivosPanelControlador {

    private final SubidaImagenServicio subidaImagenServicio;

    @Operation(
            summary = "Subir una imagen",
            description = "Acepta JPEG, PNG o WebP (máximo 5 MB). Devuelve la URL pública de la imagen."
    )
    @PostMapping(value = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ImagenSubidaResponse>> subir(
            ContextoTienda contexto,
            @Parameter(description = "Archivo de imagen", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestPart("archivo") MultipartFile archivo) {
        return RespuestaApi.creado(subidaImagenServicio.subir(contexto, archivo), "Imagen subida correctamente");
    }
}
