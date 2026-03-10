package com.sunny.datapillar.studio.dto.tenant.response;

import java.time.OffsetDateTime;
import lombok.Data;

/** Tenant API key item for management views. */
@Data
public class TenantApiKeyItemResponse {

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
  private OffsetDateTime lastUsedAt;
  private String lastUsedIp;
  private Long createdBy;
  private Long disabledBy;
  private OffsetDateTime disabledAt;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
