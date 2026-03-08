package com.sunny.datapillar.studio.integration.gravitino.model.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;

@Data
public class CatalogTestConnectionCommand {

  @NotBlank(message = "Catalog name cannot be empty")
  private String name;

  @NotBlank(message = "Catalog type cannot be empty")
  private String type;

  @NotBlank(message = "Catalog provider cannot be empty")
  private String provider;

  private String comment;

  private Map<String, String> properties;
}
