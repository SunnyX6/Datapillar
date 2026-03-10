package com.sunny.datapillar.openlineage.web.context;

/** Holder for request tenant context. */
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

  public static void clear() {
    CONTEXT.remove();
  }
}
