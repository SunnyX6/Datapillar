package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "TenantCreate")
public class TenantCreateRequest {

  @NotBlank(message = "Tenant code cannot be empty")
  @Size(max = 64, message = "The tenant code length cannot exceed64characters")
  private String code;

  @NotBlank(message = "Tenant name cannot be empty")
  @Size(max = 128, message = "The tenant name cannot be longer than128characters")
  private String name;

  @NotBlank(message = "Tenant type cannot be empty")
  @Size(max = 32, message = "The length of the tenant type cannot exceed32characters")
  private String type;
}
