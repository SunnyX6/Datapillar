package com.sunny.datapillar.common.security;

/** Claim keys used by auth assertion tokens issued for gateway trust headers. */
public final class GatewayAssertionClaims {

  public static final String AUDIENCE = "aud";
  public static final String TENANT_ID = "tenant_id";
  public static final String TENANT_CODE = "tenant_code";
  public static final String TENANT_NAME = "tenant_name";
  public static final String USERNAME = "preferred_username";
  public static final String EMAIL = "email";
  public static final String ROLES = "roles";
  public static final String IMPERSONATION = "impersonation";
  public static final String ACTOR_USER_ID = "actor_user_id";
  public static final String ACTOR_TENANT_ID = "actor_tenant_id";
  public static final String METHOD = "method";
  public static final String PATH = "path";

  private GatewayAssertionClaims() {}
}
