package shopify.api.core.soporte;

/**
 * Toda ruta HTTP usada por las pruebas se declara aquí como constante, de modo que
 * al reescribir un módulo (D-022) solo cambie este archivo y no cada prueba.
 */
public final class Rutas {

    // Autenticación (públicas)
    public static final String AUTH_REGISTRO = "/auth/registro";
    public static final String AUTH_ACTIVAR = "/auth/activar";
    public static final String AUTH_REENVIAR_ACTIVACION = "/auth/reenviar-activacion";
    public static final String AUTH_LOGIN = "/auth/login";
    public static final String AUTH_RECUPERAR_SOLICITAR = "/auth/recuperar-clave/solicitar";
    public static final String AUTH_RECUPERAR_CONFIRMAR = "/auth/recuperar-clave/confirmar";

    // Cuenta del usuario
    public static final String CUENTA_PERFIL = "/cuenta/perfil";
    public static final String CUENTA_CONTRASENA = "/cuenta/contrasena";
    public static final String CUENTA_DIRECCIONES = "/cuenta/direcciones";

    // Planes de suscripción (públicos)
    public static final String PLANES = "/planes";

    public static String cuentaDireccion(Object id) {
        return CUENTA_DIRECCIONES + "/" + id;
    }

    public static String cuentaDireccionPrincipal(Object id) {
        return CUENTA_DIRECCIONES + "/" + id + "/principal";
    }

    // Controlador de prueba del núcleo comun (solo existe en src/test)
    public static final String PRUEBA_PANEL = "/prueba-comun/panel";
    public static final String PRUEBA_PANEL_SIN_PERMISO = "/prueba-comun/panel-sin-permiso";
    public static final String PRUEBA_PLATAFORMA = "/prueba-comun/plataforma";
    public static final String PRUEBA_ERROR = "/prueba-comun/error/";
    public static final String PRUEBA_VALIDAR = "/prueba-comun/validar";
    public static final String PRUEBA_AUDITAR = "/prueba-comun/auditar";
    public static final String PRUEBA_AUDITAR_SIN_TRANSACCION = "/prueba-comun/auditar-sin-transaccion";
    public static final String RUTA_INEXISTENTE = "/ruta-que-no-existe";

    private Rutas() {
    }
}
