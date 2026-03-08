package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "UnitCreateRequest")
public class UnitCreateRequest {

  @NotBlank(message = "Unit code cannot be empty")
  private String code;

  @NotBlank(message = "Unit name cannot be empty")
  private String name;

  private String symbol;

  private String comment;
}
