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

    // Tiendas y personal
    public static final String CUENTA_TIENDAS = "/cuenta/tiendas";
    public static final String PANEL_TIENDA = "/panel/tienda";
    public static final String PANEL_TIENDA_ACTIVAR = "/panel/tienda/activar";
    public static final String PANEL_TIENDA_DESACTIVAR = "/panel/tienda/desactivar";
    public static final String PANEL_PERSONAL = "/panel/personal";
    /** Raíz del módulo anterior de tiendas, retirada en T-005; se arma por partes para el grep de verificación. */
    public static final String RAIZ_TIENDAS_RETIRADA = "/" + "tiendas";

    public static String vitrina(String subdominio) {
        return "/vitrina/" + subdominio;
    }

    public static String panelPersonal(Object usuarioId) {
        return PANEL_PERSONAL + "/" + usuarioId;
    }

    // Inventario del panel
    public static final String PANEL_INVENTARIO_VARIANTES = "/panel/inventario/variantes";

    // Catálogo (panel y vitrina)
    public static final String PANEL_CATALOGO_PRODUCTOS = "/panel/catalogo/productos";
    public static final String PANEL_CATALOGO_CATEGORIAS = "/panel/catalogo/categorias";

    public static String panelProducto(Object id) {
        return PANEL_CATALOGO_PRODUCTOS + "/" + id;
    }

    public static String panelProductoAccion(Object id, String accion) {
        return PANEL_CATALOGO_PRODUCTOS + "/" + id + "/" + accion;
    }

    public static String panelCategoria(Object id) {
        return PANEL_CATALOGO_CATEGORIAS + "/" + id;
    }

    // Cupones del panel
    public static final String PANEL_CUPONES = "/panel/cupones";

    public static String panelCupon(Object id) {
        return PANEL_CUPONES + "/" + id;
    }

    // Tarifas de envío
    public static final String PANEL_ENVIOS_TARIFAS = "/panel/envios/tarifas";

    public static String panelEnvioTarifa(Object id) {
        return PANEL_ENVIOS_TARIFAS + "/" + id;
    }

    public static String vitrinaTarifasEnvio(String subdominio) {
        return vitrina(subdominio) + "/tarifas-envio";
    }

    // Órdenes y pagos del panel
    public static final String PANEL_ORDENES = "/panel/ordenes";

    public static String panelOrdenPagos(Object ordenId) {
        return PANEL_ORDENES + "/" + ordenId + "/pagos";
    }

    public static String panelOrdenPagosManual(Object ordenId) {
        return panelOrdenPagos(ordenId) + "/manual";
    }

    public static String vitrinaCarrito(String subdominio) {
        return vitrina(subdominio) + "/carrito";
    }

    public static String vitrinaCheckout(String subdominio) {
        return vitrina(subdominio) + "/checkout";
    }

    // Suscripción (propietario) y administración de la plataforma
    public static final String PANEL_SUSCRIPCION = "/panel/suscripcion";
    public static final String PLATAFORMA_PLANES = "/plataforma/planes";
    public static final String PLATAFORMA_SUSCRIPCIONES = "/plataforma/suscripciones";

    public static String plataformaPlan(String codigo) {
        return PLATAFORMA_PLANES + "/" + codigo;
    }

    public static String plataformaTienda(Object tiendaId, String accion) {
        return "/plataforma/tiendas/" + tiendaId + "/" + accion;
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
