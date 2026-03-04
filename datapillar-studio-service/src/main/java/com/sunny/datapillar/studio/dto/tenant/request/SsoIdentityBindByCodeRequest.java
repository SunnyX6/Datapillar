package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(name = "SsoIdentityBindByCodeRequest")
public class SsoIdentityBindByCodeRequest {

  @NotNull(message = "UserIDcannot be empty")
  private Long userId;

  @NotBlank(message = "providercannot be empty")
  @Pattern(regexp = "(?i)^dingtalk$", message = "Parameter error")
  private String provider;

  @NotBlank(message = "authCodecannot be empty")
  private String authCode;
}
