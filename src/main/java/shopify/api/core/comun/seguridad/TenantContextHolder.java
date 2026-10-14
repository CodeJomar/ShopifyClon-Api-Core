package shopify.api.core.comun.seguridad;

import java.util.UUID;

/**
 * Solo para los módulos viejos. Ahora contiene únicamente tiendas ya validadas por
 * {@link ContextoTiendaInterceptor}; los módulos nuevos reciben {@link ContextoTienda} por parámetro.
 */
@Deprecated
public final class TenantContextHolder {

    private static final ThreadLocal<UUID> TENANT_ACTUAL = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantId(UUID tenantId) {
        TENANT_ACTUAL.set(tenantId);
    }

    public static UUID getTenantId() {
        return TENANT_ACTUAL.get();
    }

    public static void clear() {
        TENANT_ACTUAL.remove();
    }
}
