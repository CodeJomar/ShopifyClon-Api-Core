package shopify.api.core.modulos.usuario.modelo;

import java.time.Duration;

/** Tipos de código de un solo uso y su vigencia (USR-003, USR-005). */
public enum TipoCodigoSeguridad {

    ACTIVACION_CUENTA(Duration.ofHours(24)),
    RECUPERACION_CLAVE(Duration.ofMinutes(30)),
    /** A-007: desbloquea una cuenta bloqueada por intentos fallidos de login. */
    DESBLOQUEO_CUENTA(Duration.ofMinutes(30)),
    /** A-007: segundo paso del login cuando el usuario activó la verificación en dos pasos. */
    SEGUNDO_FACTOR(Duration.ofMinutes(10));

    private final Duration duracion;

    TipoCodigoSeguridad(Duration duracion) {
        this.duracion = duracion;
    }

    public Duration duracion() {
        return duracion;
    }
}
