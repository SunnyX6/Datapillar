package com.sunny.datapillar.studio.dto.metadata.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "TableUpdateOperationRequest")
public class TableUpdateOperationRequest {

  @JsonProperty("@type")
  private String type;

  private String newName;

  private String newComment;

  private String property;

  private String value;

  private String[] fieldName;

  @JsonProperty("type")
  private Object dataType;

  private String comment;

  private Object position;

  private Boolean nullable;

  private Boolean autoIncrement;

  private Object defaultValue;

  private String[] oldFieldName;

  private String[] newFieldName;

  private Object newDefaultValue;

  private Object newType;

  private Object newPosition;

  private Boolean ifExists;

  private Object index;

  private String name;
}
