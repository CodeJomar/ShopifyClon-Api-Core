package shopify.api.core.soporte;

/**
 * Toda ruta HTTP usada por las pruebas se declara aquí como constante, de modo que
 * al reescribir un módulo (D-022) solo cambie este archivo y no cada prueba.
 */
public final class Rutas {

    // Registro de usuarios (módulo viejo; se reemplaza en T-003)
    public static final String USUARIOS_REGISTRO = "/usuarios/registro";

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
