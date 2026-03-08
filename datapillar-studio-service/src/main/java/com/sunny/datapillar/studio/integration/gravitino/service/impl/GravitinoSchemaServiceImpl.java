package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoMetadataClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.SchemaCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.SchemaUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoSchemaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoSchemaServiceImpl implements GravitinoSchemaService {

  private final GravitinoClientFactory clientFactory;
  private final GravitinoMetadataClient metadataClient;
  private final ObjectMapper objectMapper;

  @Override
  public List<GravitinoSchemaSummaryResponse> listSchemas(String catalogName) {
    return metadataClient.listSchemas(clientFactory.requiredMetalake(), catalogName);
  }

  @Override
  public GravitinoSchemaResponse createSchema(String catalogName, SchemaCreateCommand command) {
    return metadataClient.createSchema(
        clientFactory.requiredMetalake(), catalogName, objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoSchemaResponse loadSchema(String catalogName, String schemaName) {
    return metadataClient.loadSchema(clientFactory.requiredMetalake(), catalogName, schemaName);
  }

  @Override
  public GravitinoSchemaResponse updateSchema(
      String catalogName, String schemaName, SchemaUpdateCommand command) {
    return metadataClient.updateSchema(
        clientFactory.requiredMetalake(),
        catalogName,
        schemaName,
        objectMapper.valueToTree(command));
  }

  @Override
  public boolean deleteSchema(String catalogName, String schemaName, boolean force) {
    return metadataClient.deleteSchema(
        clientFactory.requiredMetalake(), catalogName, schemaName, force);
  }

  @Override
  public boolean createSchemaIfAbsent(
      String metalake, String catalogName, SchemaCreateCommand command, String principalUsername) {
    return metadataClient.createSemanticSchema(
        metalake, catalogName, objectMapper.valueToTree(command), principalUsername);
  }

  @Override
  public boolean deleteSchema(
      String metalake,
      String catalogName,
      String schemaName,
      boolean force,
      String principalUsername) {
    return metadataClient.deleteSchema(metalake, catalogName, schemaName, force, principalUsername);
  }

  @Override
  public void setSchemaOwner(
      String metalake,
      String catalogName,
      String schemaName,
      String ownerName,
      String principalUsername) {
    metadataClient.setSchemaOwner(metalake, catalogName, schemaName, ownerName, principalUsername);
  }
}
