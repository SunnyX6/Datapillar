package com.sunny.datapillar.openlineage.web.context;

/** Holder for request trusted identity context. */
public final class TrustedIdentityContextHolder {

  private static final ThreadLocal<TrustedIdentityContext> CONTEXT = new ThreadLocal<>();

  private TrustedIdentityContextHolder() {}

  public static void set(TrustedIdentityContext context) {
    CONTEXT.set(context);
  }

  public static TrustedIdentityContext get() {
    return CONTEXT.get();
  }

  public static void clear() {
    CONTEXT.remove();
  }
}
