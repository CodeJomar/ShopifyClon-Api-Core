package shopify.api.core.comun.seguridad;

/** Permisos estables del panel (ver {@code docs/negocio/01-actores-roles-permisos.md}). */
public enum Permiso {
    TIENDA_CONFIGURAR,
    SUSCRIPCION_GESTIONAR,
    PERSONAL_GESTIONAR,
    CATALOGO_GESTIONAR,
    INVENTARIO_AJUSTAR,
    CUPONES_GESTIONAR,
    ENVIOS_CONFIGURAR,
    ORDENES_VER,
    ORDENES_GESTIONAR,
    ORDENES_CANCELAR,
    REEMBOLSOS_EMITIR,
    METRICAS_VER
}
