package com.sunny.datapillar.studio.integration.gravitino.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WordRootCreateCommand {

  @NotBlank(message = "Word root code cannot be empty")
  private String code;

  @NotBlank(message = "Word root name cannot be empty")
  private String name;

  private String dataType;

  private String comment;
}
