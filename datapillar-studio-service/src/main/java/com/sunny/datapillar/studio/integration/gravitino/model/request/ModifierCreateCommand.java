package com.sunny.datapillar.studio.integration.gravitino.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModifierCreateCommand {

  @NotBlank(message = "Modifier code cannot be empty")
  private String code;

  private String comment;

  private String modifierType;
}
