package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "ModifierCreateRequest")
public class ModifierCreateRequest {

  @NotBlank(message = "Modifier code cannot be empty")
  private String code;

  private String comment;

  private String modifierType;
}
