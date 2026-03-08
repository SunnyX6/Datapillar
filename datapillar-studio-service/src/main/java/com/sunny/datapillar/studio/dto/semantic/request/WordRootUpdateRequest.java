package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "WordRootUpdateRequest")
public class WordRootUpdateRequest {

  private String name;

  private String dataType;

  private String comment;
}
