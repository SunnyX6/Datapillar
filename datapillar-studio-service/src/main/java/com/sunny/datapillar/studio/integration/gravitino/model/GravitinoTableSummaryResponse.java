package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoTableSummaryResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String name;
}
