package com.sunny.datapillar.studio.dto.metadata.response;

import lombok.Data;

@Data
public class TableColumnResponse {

  private String name;

  private String dataType;

  private String comment;

  private boolean nullable;

  private boolean autoIncrement;

  private String defaultValue;
}
