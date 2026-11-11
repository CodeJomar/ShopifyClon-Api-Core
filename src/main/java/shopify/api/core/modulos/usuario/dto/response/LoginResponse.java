package shopify.api.core.modulos.usuario.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Resultado del login. Con la verificación en dos pasos activa (A-007) la contraseña correcta no entrega token:
 * {@code requiere_segundo_factor} viene en true y el resto ausente; el token llega en {@code POST /auth/login/verificar}.
 */
public record LoginResponse(
        String token,
        String tipo,
        Instant expiraEn,
        PerfilResponse usuario,
        List<TiendaDeUsuarioResponse> tiendas,
        boolean requiereSegundoFactor) {

    public static LoginResponse sesion(String token, Instant expiraEn, PerfilResponse usuario,
                                       List<TiendaDeUsuarioResponse> tiendas) {
        return new LoginResponse(token, "Bearer", expiraEn, usuario, tiendas, false);
    }

    public static LoginResponse pendienteDeSegundoFactor() {
        return new LoginResponse(null, null, null, null, null, true);
    }
}
