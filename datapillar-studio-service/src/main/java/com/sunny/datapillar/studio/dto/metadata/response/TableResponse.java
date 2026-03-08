package com.sunny.datapillar.studio.dto.metadata.response;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class TableResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String name;

  private String comment;

  private Map<String, String> properties;

  private List<TableColumnResponse> columns;

  private List<String> partitioning;

  private List<String> sortOrders;

  private String distribution;

  private List<String> indexes;

  private AuditResponse audit;

  private OwnerResponse owner;
}
