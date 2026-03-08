package com.sunny.datapillar.studio.integration.gravitino.model.request;

import java.util.List;
import lombok.Data;

@Data
public class TableUpdateCommand {

  private List<TableUpdateOperationCommand> updates;
}
