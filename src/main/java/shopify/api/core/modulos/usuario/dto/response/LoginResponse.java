package shopify.api.core.modulos.usuario.dto.response;

import java.time.Instant;
import java.util.List;

public record LoginResponse(
        String token,
        String tipo,
        Instant expiraEn,
        PerfilResponse usuario,
        List<TiendaDeUsuarioResponse> tiendas) {
}
