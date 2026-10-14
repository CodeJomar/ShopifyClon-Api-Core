package shopify.api.core.modulos.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import shopify.api.core.soporte.Rutas;

@ExtendWith(OutputCaptureExtension.class)
class LogsSinCodigosTest extends PruebaUsuarioBase {

    @Test
    void ningunLogContieneElCodigoEmitido(CapturedOutput salida) throws Exception {
        String correo = nuevoCorreo();
        registrar(correo);
        postJson(Rutas.AUTH_RECUPERAR_SOLICITAR, Map.of("correo_electronico", correo), null, null);
        String activacion = ultimoCodigoActivacion(correo);
        String recuperacion = ultimoCodigoRecuperacion(correo);

        // el escuchador es @Async: esperar a que registre el envío de ambos correos
        long limite = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < limite
                && !(salida.getOut().contains("Correo de activación enviado a " + correo)
                && salida.getOut().contains("Correo de recuperación enviado a " + correo))) {
            Thread.sleep(50);
        }

        for (String codigo : new String[]{activacion, recuperacion}) {
            Pattern aislado = Pattern.compile("(?<![0-9A-Za-z-])" + codigo + "(?![0-9A-Za-z-])");
            assertThat(aislado.matcher(salida.getAll()).find())
                    .as("el código %s no debe aparecer en los logs", codigo).isFalse();
        }
    }
}
