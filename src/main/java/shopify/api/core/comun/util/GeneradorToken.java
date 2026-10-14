package shopify.api.core.comun.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Genera tokens y códigos aleatorios seguros, y calcula huellas SHA-256. */
@Component
public class GeneradorToken {

    private static final int BYTES_TOKEN = 32;

    private final SecureRandom aleatorio = new SecureRandom();

    /** 32 bytes aleatorios en base64url sin relleno (43 caracteres). */
    public String tokenUrl() {
        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Código numérico de {@code digitos} cifras, con ceros a la izquierda si hace falta. */
    public String codigoNumerico(int digitos) {
        if (digitos < 1) {
            throw new IllegalArgumentException("Los dígitos deben ser al menos 1");
        }
        StringBuilder codigo = new StringBuilder(digitos);
        for (int i = 0; i < digitos; i++) {
            codigo.append(aleatorio.nextInt(10));
        }
        return codigo.toString();
    }

    public static String sha256Hex(String texto) {
        try {
            byte[] huella = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(huella);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
