package shopify.api.core.modulos.usuario.servicio;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.util.GeneradorToken;
import shopify.api.core.modulos.usuario.entidad.TokenSeguridadEntidad;
import shopify.api.core.modulos.usuario.modelo.TipoCodigoSeguridad;
import shopify.api.core.modulos.usuario.repositorio.TokenSeguridadRepositorio;

/** Emite y verifica códigos de seguridad de 6 dígitos (USR-003, 005, 006, 007, 012). Uso interno del módulo. */
@Service
@RequiredArgsConstructor
class CodigoSeguridadServicio {

    static final int MAX_INTENTOS = 5;
    static final String MENSAJE_CODIGO_INVALIDO = "El código no es válido o venció";

    private final TokenSeguridadRepositorio repositorio;
    private final GeneradorToken generadorToken;
    private final Clock reloj;

    /** Invalida los pendientes del mismo tipo y devuelve el código en claro, solo para el evento. */
    @Transactional
    public String emitir(UUID usuarioId, TipoCodigoSeguridad tipo) {
        repositorio.invalidarPendientes(usuarioId, tipo);

        String codigo = generadorToken.codigoNumerico(6);
        TokenSeguridadEntidad token = new TokenSeguridadEntidad();
        token.setUsuarioId(usuarioId);
        token.setTipo(tipo);
        token.setCodigoToken(GeneradorToken.sha256Hex(codigo));
        token.setFechaExpiracion(reloj.instant().plus(tipo.duracion()));
        token.setIntentosFallidos(0);
        repositorio.save(token);
        return codigo;
    }

    /**
     * Consume el código si es correcto. Si no lo es, cuenta el intento y lanza {@code USR-012}.
     * {@code noRollbackFor} hace que el contador persista aunque se lance la excepción; quien llame
     * a este método debe declarar lo mismo en su propia transacción.
     */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public void verificarYConsumir(UUID usuarioId, TipoCodigoSeguridad tipo, String codigo) {
        Instant ahora = reloj.instant();
        List<TokenSeguridadEntidad> pendientes =
                repositorio.bloquearPendientes(usuarioId, tipo, ahora, PageRequest.of(0, 1));
        if (pendientes.isEmpty()) {
            throw codigoInvalido();
        }

        TokenSeguridadEntidad token = pendientes.get(0);
        if (!token.getCodigoToken().equals(GeneradorToken.sha256Hex(codigo))) {
            token.setIntentosFallidos(token.getIntentosFallidos() + 1);
            if (token.getIntentosFallidos() >= MAX_INTENTOS) {
                token.setUsado(true);
            }
            repositorio.saveAndFlush(token);
            throw codigoInvalido();
        }

        token.setUsado(true);
        repositorio.save(token);
    }

    @Transactional
    public void invalidarPendientes(UUID usuarioId, TipoCodigoSeguridad tipo) {
        repositorio.invalidarPendientes(usuarioId, tipo);
    }

    private ReglaNegocioException codigoInvalido() {
        return new ReglaNegocioException("USR-012", MENSAJE_CODIGO_INVALIDO);
    }
}
