package com.sunny.datapillar.studio.integration.gravitino.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TableUpdateOperationCommand {

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
