package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoWordRootSummaryResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String code;

  private String name;
}
