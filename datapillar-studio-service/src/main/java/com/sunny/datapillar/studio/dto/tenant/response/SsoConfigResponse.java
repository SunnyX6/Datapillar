package com.sunny.datapillar.studio.dto.tenant.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "SsoConfigResponse")
public class SsoConfigResponse {

  private Long id;

  @JsonSerialize(using = ToStringSerializer.class)
  private Long tenantId;

  private String provider;

  private String baseUrl;

  private Integer status;

  private Boolean hasClientSecret;

  private SsoDingtalkConfigItem config;

  private LocalDateTime updatedAt;
}
