package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "ModifierUpdateRequest")
public class ModifierUpdateRequest {

  private String name;

  private String comment;
}
