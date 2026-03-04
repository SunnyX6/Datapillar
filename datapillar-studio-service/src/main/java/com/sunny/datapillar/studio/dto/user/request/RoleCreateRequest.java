package com.sunny.datapillar.studio.dto.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "RoleCreate")
public class RoleCreateRequest {

  @NotBlank(message = "Role name cannot be empty")
  @Size(max = 64, message = "The character name cannot be longer than64characters")
  private String name;

  @Size(max = 255, message = "Character descriptions cannot be longer than255characters")
  private String description;

  @Size(max = 16, message = "The character type length cannot exceed16characters")
  private String type;
}
