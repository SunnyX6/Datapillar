package com.sunny.datapillar.studio.dto.semantic.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "MetricUpdateOperationRequest")
public class MetricUpdateOperationRequest {

  @JsonProperty("@type")
  private String type;

  private String newName;

  private String newComment;

  private String newDataType;

  private String property;

  private String value;
}
