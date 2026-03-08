package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.SchemaCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.SchemaUpdateCommand;
import java.util.List;

public interface GravitinoSchemaService {

  List<GravitinoSchemaSummaryResponse> listSchemas(String catalogName);

  GravitinoSchemaResponse createSchema(String catalogName, SchemaCreateCommand command);

  GravitinoSchemaResponse loadSchema(String catalogName, String schemaName);

  GravitinoSchemaResponse updateSchema(
      String catalogName, String schemaName, SchemaUpdateCommand command);

  boolean deleteSchema(String catalogName, String schemaName, boolean force);

  boolean createSchemaIfAbsent(
      String metalake, String catalogName, SchemaCreateCommand command, String principalUsername);

  boolean deleteSchema(
      String metalake,
      String catalogName,
      String schemaName,
      boolean force,
      String principalUsername);

  void setSchemaOwner(
      String metalake,
      String catalogName,
      String schemaName,
      String ownerName,
      String principalUsername);
}
