package com.sunny.datapillar.studio.integration.gravitino.model.request;

import lombok.Data;

@Data
public class WordRootUpdateCommand {

  private String name;

  private String dataType;

  private String comment;
}
