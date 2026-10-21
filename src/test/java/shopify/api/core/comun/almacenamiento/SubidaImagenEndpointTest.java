package shopify.api.core.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/**
 * Pruebas de integración del endpoint {@code POST /panel/archivos/imagenes}.
 * Usa un mock de {@link AlmacenamientoImagenes} para no depender de Supabase real.
 */
class SubidaImagenEndpointTest extends PruebaIntegracionBase {

    private static final String URL_PUBLICA = "https://supabase.test/storage/v1/object/public/bucket/img.jpg";

    @MockBean
    private AlmacenamientoImagenes almacenamiento;

    private UUID usuarioId;
    private String token;
    private UUID tiendaId;

    @BeforeEach
    void preparar() {
        usuarioId = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject(
                "SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuarioId);
        token = tokenPara(usuarioId, correo);
        tiendaId = crearTiendaOperativa(usuarioId);
    }

    // ---- Éxito ----

    @Test
    void subirJpegDevuelve201ConUrl() throws Exception {
        when(almacenamiento.subir(eq(tiendaId), any(byte[].class), eq("image/jpeg")))
                .thenReturn(URL_PUBLICA);

        subirArchivo(jpegValido(), "imagen.jpg")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.url").value(URL_PUBLICA))
                .andExpect(jsonPath("$.mensaje").value("Imagen subida correctamente"));
    }

    @Test
    void subirPngDevuelve201() throws Exception {
        when(almacenamiento.subir(eq(tiendaId), any(byte[].class), eq("image/png")))
                .thenReturn(URL_PUBLICA);

        subirArchivo(pngValido(), "foto.png")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.url").value(URL_PUBLICA));
    }

    @Test
    void subirWebpDevuelve201() throws Exception {
        when(almacenamiento.subir(eq(tiendaId), any(byte[].class), eq("image/webp")))
                .thenReturn(URL_PUBLICA);

        subirArchivo(webpValido(), "foto.webp")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.url").value(URL_PUBLICA));
    }

    // ---- Tipo inválido ----

    @Test
    void textoRenombradoAPngSeRechaza422() throws Exception {
        byte[] texto = "esto no es una imagen real".getBytes();

        subirArchivo(texto, "trampa.png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ALM-004"))
                .andExpect(jsonPath("$.mensaje").exists());

        verify(almacenamiento, never()).subir(any(), any(), any());
    }

    @Test
    void gifSeRechaza422() throws Exception {
        byte[] gif = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0, 0, 0};

        subirArchivo(gif, "animacion.gif")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ALM-004"));

        verify(almacenamiento, never()).subir(any(), any(), any());
    }

    // ---- Tamaño excedido ----

    @Test
    void archivoMayorA5MbSeRechaza() throws Exception {
        // 5 MB + 1 byte, con magic bytes de JPEG para pasar la validación de tipo
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;

        subirArchivo(grande, "enorme.jpg")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ALM-005"));

        verify(almacenamiento, never()).subir(any(), any(), any());
    }

    // ---- Sin permiso (403) ----

    @Test
    void sinPermisoDevuelve403() throws Exception {
        UUID otroPropietario = crearUsuarioActivo(false);
        UUID otraTienda = crearTiendaOperativa(otroPropietario);

        // GESTOR_PEDIDOS tiene permisos de órdenes pero NO CATALOGO_GESTIONAR
        agregarPersonal(otraTienda, usuarioId, "GESTOR_PEDIDOS");

        mvc.perform(MockMvcRequestBuilders.multipart(Rutas.PANEL_ARCHIVOS_IMAGENES)
                        .file(new MockMultipartFile("archivo", "img.jpg", "image/jpeg", jpegValido()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tienda-Id", otraTienda.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEG-002"));
    }

    // ---- Tienda ajena (404) ----

    @Test
    void tiendaAjenaDevuelve404() throws Exception {
        UUID otroUsuario = crearUsuarioActivo(false);
        UUID otraTienda = crearTiendaOperativa(otroUsuario);

        mvc.perform(MockMvcRequestBuilders.multipart(Rutas.PANEL_ARCHIVOS_IMAGENES)
                        .file(new MockMultipartFile("archivo", "img.jpg", "image/jpeg", jpegValido()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tienda-Id", otraTienda.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEG-001"));
    }

    // ---- Sin autenticación (401) ----

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart(Rutas.PANEL_ARCHIVOS_IMAGENES)
                        .file(new MockMultipartFile("archivo", "img.jpg", "image/jpeg", jpegValido())))
                .andExpect(status().isUnauthorized());
    }

    // ---- Almacenamiento no configurado (503) ----

    @Test
    void almacenamientoNoConfiguradoDevuelve503() throws Exception {
        when(almacenamiento.subir(any(), any(), any()))
                .thenThrow(new AlmacenamientoNoConfiguradoException("ALM-001",
                        "El servicio de almacenamiento de imágenes no está configurado"));

        subirArchivo(jpegValido(), "foto.jpg")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("ALM-001"));
    }

    // ---- PNG renombrado a .exe se acepta (criterio de aceptación) ----

    @Test
    void pngRenombradoAExeSeAceptaSiBytesSonPng() throws Exception {
        when(almacenamiento.subir(eq(tiendaId), any(byte[].class), eq("image/png")))
                .thenReturn(URL_PUBLICA);

        subirArchivo(pngValido(), "programa.exe")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.url").value(URL_PUBLICA));
    }

    // ---- Criterio de aceptación: La URL devuelta funciona en POST /panel/catalogo/productos/{id}/imagenes ----

    @Test
    void urlDevueltaFuncionaEnEndpointDeAgregarImagenAProducto() throws Exception {
        when(almacenamiento.subir(eq(tiendaId), any(byte[].class), eq("image/jpeg")))
                .thenReturn(URL_PUBLICA);

        // 1. Subir imagen
        ResultActions resSubida = subirArchivo(jpegValido(), "producto.jpg")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.url").value(URL_PUBLICA));

        String urlObtenida = cuerpo(resSubida).get("datos").get("url").asText();

        // 2. Crear un producto
        Map<String, Object> variante = Map.of("sku", "SKU-IMG-" + sufijo(), "precio", 100);
        Map<String, Object> prod = Map.of("titulo", "Producto Con Imagen", "variantes", List.of(variante));
        JsonNode prodCreado = cuerpo(postJson(Rutas.PANEL_CATALOGO_PRODUCTOS, prod, token, tiendaId)
                .andExpect(status().isCreated())).get("datos");
        UUID productoId = UUID.fromString(prodCreado.get("id").asText());

        // 3. Agregar la imagen subida al producto con POST /panel/catalogo/productos/{id}/imagenes
        Map<String, Object> imagenReq = Map.of(
                "url_imagen", urlObtenida,
                "texto_alternativo", "Foto principal"
        );
        postJson(Rutas.panelProductoAccion(productoId, "imagenes"), imagenReq, token, tiendaId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.url_imagen").value(URL_PUBLICA))
                .andExpect(jsonPath("$.datos.es_principal").value(true));
    }

    // ---- Criterio de aceptación: El endpoint aparece en Swagger con su esquema multipart ----

    @Test
    void endpointApareceEnOpenApiConEsquemaMultipart() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/panel/archivos/imagenes'].post").exists())
                .andExpect(jsonPath("$.paths['/panel/archivos/imagenes'].post.requestBody.content['multipart/form-data']").exists());
    }

    // ---- Helpers ----

    private ResultActions subirArchivo(byte[] contenido, String nombre) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.multipart(Rutas.PANEL_ARCHIVOS_IMAGENES)
                .file(new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Tienda-Id", tiendaId.toString()));
    }

    private byte[] jpegValido() {
        byte[] datos = new byte[20];
        datos[0] = (byte) 0xFF;
        datos[1] = (byte) 0xD8;
        datos[2] = (byte) 0xFF;
        datos[3] = (byte) 0xE0;
        return datos;
    }

    private byte[] pngValido() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0};
    }

    private byte[] webpValido() {
        return new byte[]{0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0};
    }
}
