package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoTableColumnResponse {

  private String name;

  private String dataType;

  private String comment;

  private boolean nullable;

  private boolean autoIncrement;

  private String defaultValue;
}
