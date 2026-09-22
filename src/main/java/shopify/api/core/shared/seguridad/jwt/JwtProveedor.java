package shopify.api.core.shared.seguridad.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtProveedor {

    private final SecretKey claveFirma;
    private final long tiempoExpiracionMs;

    public JwtProveedor(
            @Value("${jwt.secreto}") String secreto,
            @Value("${jwt.expiracion-ms}") long tiempoExpiracionMs) {
        // Decodifica la clave secreta en Base64 o genera la clave HMAC adecuada
        byte[] bytesClave = Decoders.BASE64.decode(secreto);
        this.claveFirma = Keys.hmacShaKeyFor(bytesClave);
        this.tiempoExpiracionMs = tiempoExpiracionMs;
    }

    /**
     * Genera un token JWT con el UUID del usuario como Subject y claims adicionales.
     */
    public String generarToken(UUID usuarioId, String correo, Map<String, Object> claimsAdicionales) {
        Date ahora = new Date();
        Date fechaExpiracion = new Date(ahora.getTime() + this.tiempoExpiracionMs);

        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("correo", correo)
                .claims(claimsAdicionales)
                .issuedAt(ahora)
                .expiration(fechaExpiracion)
                .signWith(this.claveFirma)
                .compact();
    }

    /**
     * Extrae el UUID del usuario contenido en el Subject del JWT.
     */
    public UUID extraerUsuarioId(String token) {
        Claims claims = extraerTodosLosClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extrae el correo electrónico guardado en los claims.
     */
    public String extraerCorreo(String token) {
        return extraerTodosLosClaims(token).get("correo", String.class);
    }

    /**
     * Valida si el token es estructuralmente válido, no ha expirado y la firma coincide.
     */
    public boolean esTokenValido(String token) {
        try {
            extraerTodosLosClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extraerTodosLosClaims(String token) {
        return Jwts.parser()
                .verifyWith(this.claveFirma)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}