package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "UnitUpdateRequest")
public class UnitUpdateRequest {

  private String name;

  private String symbol;

  private String comment;
}
