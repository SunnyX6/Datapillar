package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoCatalogSummaryResponse {

  private String metalake;

  private String name;

  private String type;

  private String provider;
}
