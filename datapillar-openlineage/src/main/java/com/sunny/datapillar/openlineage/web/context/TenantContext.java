package com.sunny.datapillar.openlineage.web.context;

/** Request tenant context. */
public class TenantContext {

  private final Long tenantId;
  private final String tenantCode;

  public TenantContext(Long tenantId, String tenantCode) {
    this.tenantId = tenantId;
    this.tenantCode = tenantCode;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public String getTenantCode() {
    return tenantCode;
  }
}
