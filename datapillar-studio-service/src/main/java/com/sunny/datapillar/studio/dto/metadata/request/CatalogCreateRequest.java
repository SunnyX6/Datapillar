package com.sunny.datapillar.studio.dto.metadata.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "CatalogCreateRequest")
public class CatalogCreateRequest {

  @NotBlank(message = "Catalog name cannot be empty")
  private String name;

  @NotBlank(message = "Catalog type cannot be empty")
  private String type;

  @NotBlank(message = "Catalog provider cannot be empty")
  private String provider;

  private String comment;

  private Map<String, String> properties;
}
