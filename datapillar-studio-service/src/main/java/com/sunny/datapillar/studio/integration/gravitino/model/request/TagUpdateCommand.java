package com.sunny.datapillar.studio.integration.gravitino.model.request;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class TagUpdateCommand {

  private String comment;

  private Map<String, String> properties;

  private List<TagUpdateOperationCommand> updates;
}
