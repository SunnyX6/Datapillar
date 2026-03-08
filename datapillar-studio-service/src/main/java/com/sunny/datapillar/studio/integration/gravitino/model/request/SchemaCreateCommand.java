package com.sunny.datapillar.studio.integration.gravitino.model.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;

@Data
public class SchemaCreateCommand {

  @NotBlank(message = "Schema name cannot be empty")
  private String name;

  private String comment;

  private Map<String, String> properties;
}
