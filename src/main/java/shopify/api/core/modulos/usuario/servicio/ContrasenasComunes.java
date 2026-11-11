package shopify.api.core.modulos.usuario.servicio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import shopify.api.core.comun.excepcion.ReglaNegocioException;

/**
 * A-007 (USR-015): rechaza contraseñas que cumplen el formato pero son de las primeras que prueba un atacante
 * (lista en {@code seguridad/contrasenas-comunes.txt}) o que contienen el nombre del propio correo.
 */
@Component
class ContrasenasComunes {

    private static final String RECURSO = "seguridad/contrasenas-comunes.txt";
    private static final int MINIMO_PARTE_CORREO = 4;

    private final Set<String> comunes;

    ContrasenasComunes() {
        this.comunes = cargar();
    }

    void exigirSegura(String contrasena, String correo) {
        String clave = contrasena.toLowerCase(Locale.ROOT);
        if (comunes.contains(clave)) {
            throw new ReglaNegocioException("USR-015", "Esa contraseña es demasiado común. Elige una más difícil de adivinar");
        }
        String usuarioCorreo = correo == null ? "" : correo.toLowerCase(Locale.ROOT).split("@", 2)[0];
        if (usuarioCorreo.length() >= MINIMO_PARTE_CORREO && clave.contains(usuarioCorreo)) {
            throw new ReglaNegocioException("USR-015", "La contraseña no puede contener tu correo");
        }
    }

    private static Set<String> cargar() {
        Set<String> lista = new HashSet<>();
        try (InputStream entrada = ContrasenasComunes.class.getClassLoader().getResourceAsStream(RECURSO)) {
            if (entrada == null) {
                throw new IllegalStateException("Falta el recurso " + RECURSO);
            }
            BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            String linea;
            while ((linea = lector.readLine()) != null) {
                String limpia = linea.strip().toLowerCase(Locale.ROOT);
                if (!limpia.isEmpty() && !limpia.startsWith("#")) {
                    lista.add(limpia);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return Set.copyOf(lista);
    }
}
