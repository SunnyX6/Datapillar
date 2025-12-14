package com.sunny.kg.tenant;

/**
 * 租户上下文
 * <p>
 * 使用 ThreadLocal 存储当前租户信息
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    /**
     * 设置当前租户
     */
    public static void setTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * 获取当前租户
     */
    public static String getTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * 清除租户信息
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * 在指定租户上下文中执行
     */
    public static <T> T executeWithTenant(String tenantId, TenantSupplier<T> supplier) {
        String previous = getTenant();
        try {
            setTenant(tenantId);
            return supplier.get();
        } finally {
            if (previous != null) {
                setTenant(previous);
            } else {
                clear();
            }
        }
    }

    /**
     * 在指定租户上下文中执行（无返回值）
     */
    public static void executeWithTenant(String tenantId, Runnable runnable) {
        executeWithTenant(tenantId, () -> {
            runnable.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface TenantSupplier<T> {
        T get();
    }

}
