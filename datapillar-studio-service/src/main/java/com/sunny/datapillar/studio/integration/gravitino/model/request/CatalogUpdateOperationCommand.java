package com.sunny.datapillar.studio.integration.gravitino.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CatalogUpdateOperationCommand {

  @JsonProperty("@type")
  private String type;

  private String newName;

  private String newComment;

  private String property;

  private String value;
}
