package com.sunny.datapillar.common.constant;

/**
 * Request header constants Centrally maintain request header constant definitions
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class HeaderConstants {

  public static final String HEADER_TRACE_ID = "X-Trace-Id";
  public static final String HEADER_REQUEST_ID = "X-Request-Id";
  public static final String HEADER_TENANT_ID = "X-Tenant-Id";
  public static final String HEADER_TENANT_CODE = "X-Tenant-Code";
  public static final String HEADER_USER_ID = "X-User-Id";
  public static final String HEADER_USERNAME = "X-Username";
  public static final String HEADER_EMAIL = "X-User-Email";
  public static final String HEADER_USER_ROLES = "X-User-Roles";
  public static final String HEADER_PRINCIPAL_ISS = "X-Principal-Iss";
  public static final String HEADER_PRINCIPAL_SUB = "X-Principal-Sub";
  public static final String HEADER_ACTOR_USER_ID = "X-Actor-User-Id";
  public static final String HEADER_ACTOR_TENANT_ID = "X-Actor-Tenant-Id";
  public static final String HEADER_IMPERSONATION = "X-Impersonation";

  private HeaderConstants() {}
}
