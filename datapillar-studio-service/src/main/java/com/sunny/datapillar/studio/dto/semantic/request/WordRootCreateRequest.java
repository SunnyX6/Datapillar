package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "WordRootCreateRequest")
public class WordRootCreateRequest {

  @NotBlank(message = "Word root code cannot be empty")
  private String code;

  @NotBlank(message = "Word root name cannot be empty")
  private String name;

  private String dataType;

  private String comment;
}
