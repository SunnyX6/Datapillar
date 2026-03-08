package com.sunny.datapillar.studio.integration.gravitino.model.request;

import java.util.List;
import lombok.Data;

@Data
public class ObjectTagAlterCommand {

  private List<String> tagsToAdd;

  private List<String> tagsToRemove;
}
