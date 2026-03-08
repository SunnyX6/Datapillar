package com.sunny.datapillar.studio.integration.gravitino.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UnitCreateCommand {

  @NotBlank(message = "Unit code cannot be empty")
  private String code;

  @NotBlank(message = "Unit name cannot be empty")
  private String name;

  private String symbol;

  private String comment;
}
