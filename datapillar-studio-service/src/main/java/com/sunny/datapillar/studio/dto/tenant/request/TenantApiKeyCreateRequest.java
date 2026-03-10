package com.sunny.datapillar.studio.dto.tenant.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.Data;

/** Request payload for tenant API key creation. */
@Data
public class TenantApiKeyCreateRequest {

  @NotBlank(message = "name must not be blank")
  @Size(max = 64, message = "name length must be <= 64")
  private String name;

  @Size(max = 255, message = "description length must be <= 255")
  private String description;

  private OffsetDateTime expiresAt;
}
