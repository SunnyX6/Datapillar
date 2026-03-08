package com.sunny.datapillar.studio.dto.metadata.response;

import lombok.Data;

@Data
public class TableSummaryResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String name;
}
