package com.sunny.datapillar.studio.integration.gravitino.model.request;

import lombok.Data;

@Data
public class UnitUpdateCommand {

  private String name;

  private String symbol;

  private String comment;
}
