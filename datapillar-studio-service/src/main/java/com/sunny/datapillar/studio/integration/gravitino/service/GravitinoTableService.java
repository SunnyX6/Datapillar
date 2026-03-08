package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TableCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TableUpdateCommand;
import java.util.List;

public interface GravitinoTableService {

  List<GravitinoTableSummaryResponse> listTables(String catalogName, String schemaName);

  GravitinoTableResponse createTable(
      String catalogName, String schemaName, TableCreateCommand command);

  GravitinoTableResponse loadTable(String catalogName, String schemaName, String tableName);

  GravitinoTableResponse updateTable(
      String catalogName, String schemaName, String tableName, TableUpdateCommand command);

  boolean deleteTable(String catalogName, String schemaName, String tableName, boolean force);
}
