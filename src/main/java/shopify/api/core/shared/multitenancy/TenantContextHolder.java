package shopify.api.core.shared.multitenancy;

import java.util.UUID;

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