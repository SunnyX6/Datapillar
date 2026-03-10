package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Tenant node model for graph ownership root. */
@Data
public class Tenant {
  private Long tenantId;
  private String tenantCode;
  private String tenantName;
}
