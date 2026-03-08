package com.sunny.datapillar.studio.dto.metadata.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "SchemaCreateRequest")
public class SchemaCreateRequest {

  @NotBlank(message = "Schema name cannot be empty")
  private String name;

  private String comment;

  private Map<String, String> properties;
}
