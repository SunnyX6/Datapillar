package com.sunny.datapillar.studio.dto.tenant.request;

import com.sunny.datapillar.studio.dto.tenant.response.SsoDingtalkConfigItem;
import com.sunny.datapillar.studio.validation.ValidSsoConfigCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@ValidSsoConfigCreateRequest
@Schema(name = "SsoConfigCreate")
public class SsoConfigCreateRequest {

  @NotBlank(message = "SSOProvider cannot be empty")
  @Pattern(regexp = "(?i)^dingtalk$", message = "Parameter error")
  @Size(max = 32, message = "SSOProvider length cannot exceed32characters")
  private String provider;

  @Size(max = 255, message = "BasicsURLThe length cannot exceed255characters")
  private String baseUrl;

  @Valid private SsoDingtalkConfigItem config;

  @Min(value = 0, message = "Parameter error")
  @Max(value = 1, message = "Parameter error")
  private Integer status;
}
