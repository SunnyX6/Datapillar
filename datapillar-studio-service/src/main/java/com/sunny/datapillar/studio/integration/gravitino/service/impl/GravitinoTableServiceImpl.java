package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoMetadataClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TableCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TableUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoTableService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoTableServiceImpl implements GravitinoTableService {

  private final GravitinoMetadataClient metadataClient;
  private final GravitinoClientFactory clientFactory;
  private final ObjectMapper objectMapper;

  @Override
  public List<GravitinoTableSummaryResponse> listTables(String catalogName, String schemaName) {
    return metadataClient.listTables(clientFactory.requiredMetalake(), catalogName, schemaName);
  }

  @Override
  public GravitinoTableResponse createTable(
      String catalogName, String schemaName, TableCreateCommand command) {
    return metadataClient.createTable(
        clientFactory.requiredMetalake(),
        catalogName,
        schemaName,
        objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoTableResponse loadTable(String catalogName, String schemaName, String tableName) {
    return metadataClient.loadTable(
        clientFactory.requiredMetalake(), catalogName, schemaName, tableName);
  }

  @Override
  public GravitinoTableResponse updateTable(
      String catalogName, String schemaName, String tableName, TableUpdateCommand command) {
    return metadataClient.updateTable(
        clientFactory.requiredMetalake(),
        catalogName,
        schemaName,
        tableName,
        objectMapper.valueToTree(command));
  }

  @Override
  public boolean deleteTable(
      String catalogName, String schemaName, String tableName, boolean force) {
    return metadataClient.deleteTable(
        clientFactory.requiredMetalake(), catalogName, schemaName, tableName, force);
  }
}
