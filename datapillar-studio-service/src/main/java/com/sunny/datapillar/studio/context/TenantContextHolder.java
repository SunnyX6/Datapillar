package com.sunny.datapillar.studio.context;

/**
 * Tenant contextHoldercomponents Responsible for tenant contextHolderCore logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class TenantContextHolder {
  private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

  private TenantContextHolder() {}

  public static void set(TenantContext context) {
    CONTEXT.set(context);
  }

  public static TenantContext get() {
    return CONTEXT.get();
  }

  public static Long getTenantId() {
    TenantContext context = CONTEXT.get();
    return context == null ? null : context.getTenantId();
  }

  public static String getTenantCode() {
    TenantContext context = CONTEXT.get();
    return context == null ? null : context.getTenantCode();
  }

  public static Long getActorUserId() {
    TenantContext context = CONTEXT.get();
    return context == null ? null : context.getActorUserId();
  }

  public static Long getActorTenantId() {
    TenantContext context = CONTEXT.get();
    return context == null ? null : context.getActorTenantId();
  }

  public static boolean isImpersonation() {
    TenantContext context = CONTEXT.get();
    return context != null && context.isImpersonation();
  }

  public static void clear() {
    CONTEXT.remove();
  }
}
