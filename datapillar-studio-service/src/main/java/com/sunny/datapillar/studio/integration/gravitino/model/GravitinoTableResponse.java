package com.sunny.datapillar.studio.integration.gravitino.model;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class GravitinoTableResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String name;

  private String comment;

  private Map<String, String> properties;

  private List<GravitinoTableColumnResponse> columns;

  private List<String> partitioning;

  private List<String> sortOrders;

  private String distribution;

  private List<String> indexes;

  private GravitinoAuditResponse audit;

  private GravitinoOwnerResponse owner;
}
