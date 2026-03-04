package com.sunny.datapillar.studio.dto.tenant.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "TenantFeatureAuditItem")
public class TenantFeatureAuditItem {

  private Long id;

  @JsonSerialize(using = ToStringSerializer.class)
  private Long tenantId;

  private String action;

  private String detail;

  private Long operatorUserId;

  private LocalDateTime createdAt;
}
