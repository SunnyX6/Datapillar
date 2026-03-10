package com.sunny.datapillar.studio.dto.tenant.response;

import java.time.OffsetDateTime;
import lombok.Data;

/** Tenant API key creation response. */
@Data
public class TenantApiKeyCreateResponse {

  private Long id;
  private String name;
  private String description;
  private String authType;
  private String apiDomain;
  private String headerName;
  private String headerScheme;
  private String lastFour;
  private Integer status;
  private OffsetDateTime expiresAt;
  private OffsetDateTime createdAt;
  private String plainApiKey;
  private String usageExample;
}
