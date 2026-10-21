package shopify.api.core.comun.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IP del visitante para el límite de peticiones (A-002). Detrás del proxy de la web todas las peticiones llegan con
 * la IP del servidor web; la IP real viene en {@code X-Forwarded-For}, pero solo se confía en esa cabecera si el
 * proxy demuestra que la envía con el secreto compartido ({@code X-Proxy-Secreto}). Con cualquier otra cosa se usa
 * {@link HttpServletRequest#getRemoteAddr()}. El secreto nunca se registra en logs.
 */
@Component
public class IpClienteResolver {

    private static final String CABECERA_IP = "X-Forwarded-For";
    private static final String CABECERA_SECRETO = "X-Proxy-Secreto";
    private static final Pattern IPV4 = Pattern.compile(
            "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern GRUPO_IPV6 = Pattern.compile("[0-9A-Fa-f]{1,4}");

    /** Huella SHA-256 del secreto; {@code null} si el secreto está vacío y la cabecera no se confía nunca. */
    private final byte[] huellaSecreto;

    public IpClienteResolver(@Value("${app.proxy.secreto:}") String secreto) {
        this.huellaSecreto = secreto == null || secreto.isBlank() ? null : huella(secreto);
    }

    /** La IP de {@code X-Forwarded-For} si el proxy está autenticado y es una IP válida; si no, la IP de la conexión. */
    public String ipDe(HttpServletRequest request) {
        String ipConexion = request.getRemoteAddr();
        if (huellaSecreto == null || !secretoCoincide(request.getHeader(CABECERA_SECRETO))) {
            return ipConexion;
        }
        String reenviada = request.getHeader(CABECERA_IP);
        return esIp(reenviada) ? reenviada.strip() : ipConexion;
    }

    /** Compara huellas de longitud fija con {@link MessageDigest#isEqual}: el tiempo no depende del secreto. */
    private boolean secretoCoincide(String enviado) {
        if (enviado == null) {
            return false;
        }
        return MessageDigest.isEqual(huellaSecreto, huella(enviado));
    }

    /** Una sola IP, v4 o v6. Sin resolución DNS: un texto que no sea IP se rechaza sin consultar nada. */
    static boolean esIp(String valor) {
        if (valor == null) {
            return false;
        }
        String ip = valor.strip();
        if (ip.isEmpty() || ip.contains(",")) {
            return false;
        }
        return IPV4.matcher(ip).matches() || esIpv6(ip);
    }

    /**
     * IPv6 en notación textual con grupos hexadecimales (sin sufijo IPv4 embebido: esas direcciones caen en la IP
     * de la conexión, decisión conservadora registrada en el reporte A-002).
     */
    private static boolean esIpv6(String ip) {
        if (ip.length() > 39 || !ip.contains(":")) {
            return false;
        }
        if (ip.startsWith(":") && !ip.startsWith("::") || ip.endsWith(":") && !ip.endsWith("::")) {
            return false;
        }
        int dobles = 0;
        for (int i = 0; i < ip.length() - 1; i++) {
            if (ip.startsWith("::", i)) {
                dobles++;
            }
        }
        if (dobles > 1) {
            return false;
        }
        int grupos = 0;
        for (String grupo : ip.split(":", -1)) {
            if (grupo.isEmpty()) {
                continue;
            }
            if (!GRUPO_IPV6.matcher(grupo).matches()) {
                return false;
            }
            grupos++;
        }
        return dobles == 0 ? grupos == 8 : grupos <= 7;
    }

    private static byte[] huella(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
