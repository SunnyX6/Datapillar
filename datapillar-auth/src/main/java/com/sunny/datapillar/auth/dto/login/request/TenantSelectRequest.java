package com.sunny.datapillar.auth.dto.login.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "AuthLoginTenantRequest")
public class TenantSelectRequest {

  @NotBlank(message = "loginToken must not be blank")
  private String loginToken;

  @NotNull(message = "tenantId must not be null")
  private Long tenantId;
}
