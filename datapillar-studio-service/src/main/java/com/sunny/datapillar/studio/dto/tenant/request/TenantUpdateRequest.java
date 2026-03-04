package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "TenantUpdate")
public class TenantUpdateRequest {

  @Size(max = 128, message = "The tenant name cannot be longer than128characters")
  private String name;

  @Size(max = 32, message = "The length of the tenant type cannot exceed32characters")
  private String type;
}
