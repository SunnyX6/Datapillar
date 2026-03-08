package com.sunny.datapillar.studio.dto.metadata.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "CatalogUpdateOperationRequest")
public class CatalogUpdateOperationRequest {

  @JsonProperty("@type")
  private String type;

  private String newName;

  private String newComment;

  private String property;

  private String value;
}
