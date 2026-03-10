package com.sunny.datapillar.studio.integration.gravitino;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoOwnerResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagSummaryResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.CatalogChange;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.MetadataObjects;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.Schema;
import org.apache.gravitino.SchemaChange;
import org.apache.gravitino.authorization.Owner;
import org.apache.gravitino.client.GravitinoAdminClient;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.dto.requests.CatalogCreateRequest;
import org.apache.gravitino.dto.requests.CatalogUpdateRequest;
import org.apache.gravitino.dto.requests.CatalogUpdatesRequest;
import org.apache.gravitino.dto.requests.SchemaCreateRequest;
import org.apache.gravitino.dto.requests.SchemaUpdateRequest;
import org.apache.gravitino.dto.requests.SchemaUpdatesRequest;
import org.apache.gravitino.dto.requests.TableCreateRequest;
import org.apache.gravitino.dto.requests.TableUpdateRequest;
import org.apache.gravitino.dto.requests.TableUpdatesRequest;
import org.apache.gravitino.dto.requests.TagCreateRequest;
import org.apache.gravitino.dto.requests.TagUpdateRequest;
import org.apache.gravitino.dto.requests.TagUpdatesRequest;
import org.apache.gravitino.dto.requests.TagsAssociateRequest;
import org.apache.gravitino.dto.util.DTOConverters;
import org.apache.gravitino.exceptions.CatalogAlreadyExistsException;
import org.apache.gravitino.exceptions.MetalakeAlreadyExistsException;
import org.apache.gravitino.exceptions.SchemaAlreadyExistsException;
import org.apache.gravitino.rel.Table;
import org.apache.gravitino.rel.TableCatalog;
import org.apache.gravitino.rel.TableChange;
import org.apache.gravitino.tag.SupportsTags;
import org.apache.gravitino.tag.Tag;
import org.springframework.stereotype.Component;

@Component
public class GravitinoMetadataClient {

  private final GravitinoClientFactory clientFactory;
  private final ObjectMapper objectMapper;
  private final GravitinoExceptionMapper errorMapper;

  public GravitinoMetadataClient(
      GravitinoClientFactory clientFactory,
      ObjectMapper objectMapper,
      GravitinoExceptionMapper errorMapper) {
    this.clientFactory = clientFactory;
    this.objectMapper = objectMapper;
    this.errorMapper = errorMapper;
  }

  public boolean createMetalake(
      String metalakeName, String comment, JsonNode properties, String principalUsername) {
    String normalizedMetalakeName = requireName(metalakeName, "metalakeName");
    try (GravitinoAdminClient client = clientFactory.createAdminClient(principalUsername)) {
      try {
        client.createMetalake(normalizedMetalakeName, comment, parseStringMap(properties));
        return true;
      } catch (MetalakeAlreadyExistsException ignored) {
        return false;
      }
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public List<GravitinoCatalogSummaryResponse> listCatalogs(String metalake) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return Arrays.stream(client.listCatalogsInfo())
          .map(catalog -> GravitinoDtoMapper.mapCatalogSummary(managedMetalake, catalog))
          .toList();
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public void testCatalogConnection(String metalake, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    CatalogCreateRequest request =
        objectMapper.convertValue(nullSafeBody(body), CatalogCreateRequest.class);
    request.validate();
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      runCatalogConnectionTest(client, request);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  @SneakyThrows
  private void runCatalogConnectionTest(GravitinoClient client, CatalogCreateRequest request) {
    client.testConnection(
        request.getName(),
        request.getType(),
        request.getProvider(),
        request.getComment(),
        nullSafeMap(request.getProperties()));
  }

  public GravitinoCatalogResponse createCatalog(String metalake, JsonNode body) {
    return createCatalogInternal(metalake, body, null, false);
  }

  public boolean createSemanticCatalog(String metalake, JsonNode body, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    CatalogCreateRequest request =
        objectMapper.convertValue(nullSafeBody(body), CatalogCreateRequest.class);
    request.validate();
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      try {
        client.createCatalog(
            request.getName(),
            request.getType(),
            request.getProvider(),
            request.getComment(),
            nullSafeMap(request.getProperties()));
        return true;
      } catch (CatalogAlreadyExistsException ignored) {
        return false;
      }
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoCatalogResponse loadCatalog(String metalake, String catalogName) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Catalog catalog = client.loadCatalog(normalizedCatalogName);
      return GravitinoDtoMapper.mapCatalog(
          managedMetalake,
          catalog,
          loadOwner(client, MetadataObjects.of(null, catalog.name(), MetadataObject.Type.CATALOG)));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoCatalogResponse updateCatalog(
      String metalake, String catalogName, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Catalog currentCatalog = client.loadCatalog(normalizedCatalogName);
      CatalogChange[] changes = parseCatalogChanges(normalizedBody, currentCatalog);
      Catalog catalog =
          changes.length == 0
              ? currentCatalog
              : client.alterCatalog(normalizedCatalogName, changes);
      return GravitinoDtoMapper.mapCatalog(
          managedMetalake,
          catalog,
          loadOwner(client, MetadataObjects.of(null, catalog.name(), MetadataObject.Type.CATALOG)));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean deleteCatalog(String metalake, String catalogName, boolean force) {
    return deleteCatalog(metalake, catalogName, force, null);
  }

  public boolean deleteCatalog(
      String metalake, String catalogName, boolean force, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      return client.dropCatalog(normalizedCatalogName, force);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public List<GravitinoSchemaSummaryResponse> listSchemas(String metalake, String catalogName) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Catalog catalog = client.loadCatalog(normalizedCatalogName);
      return Arrays.stream(catalog.asSchemas().listSchemas())
          .map(
              schemaName ->
                  GravitinoDtoMapper.mapSchemaSummary(
                      managedMetalake, normalizedCatalogName, schemaName))
          .toList();
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoSchemaResponse createSchema(String metalake, String catalogName, JsonNode body) {
    return createSchemaInternal(metalake, catalogName, body, null, false);
  }

  public boolean createSemanticSchema(
      String metalake, String catalogName, JsonNode body, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    SchemaCreateRequest request =
        objectMapper.convertValue(nullSafeBody(body), SchemaCreateRequest.class);
    request.validate();
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      try {
        client
            .loadCatalog(normalizedCatalogName)
            .asSchemas()
            .createSchema(
                request.getName(), request.getComment(), nullSafeMap(request.getProperties()));
        return true;
      } catch (SchemaAlreadyExistsException ignored) {
        return false;
      }
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoSchemaResponse loadSchema(
      String metalake, String catalogName, String schemaName) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    String normalizedSchemaName = requireName(schemaName, "schemaName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Catalog catalog = client.loadCatalog(normalizedCatalogName);
      Schema schema = catalog.asSchemas().loadSchema(normalizedSchemaName);
      return GravitinoDtoMapper.mapSchema(
          managedMetalake,
          normalizedCatalogName,
          schema,
          loadOwner(
              client,
              MetadataObjects.of(
                  normalizedCatalogName, schema.name(), MetadataObject.Type.SCHEMA)));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoSchemaResponse updateSchema(
      String metalake, String catalogName, String schemaName, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    String normalizedSchemaName = requireName(schemaName, "schemaName");
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Catalog catalog = client.loadCatalog(normalizedCatalogName);
      Schema currentSchema = catalog.asSchemas().loadSchema(normalizedSchemaName);
      SchemaChange[] changes = parseSchemaChanges(normalizedBody, currentSchema);
      Schema schema =
          changes.length == 0
              ? currentSchema
              : catalog.asSchemas().alterSchema(normalizedSchemaName, changes);
      return GravitinoDtoMapper.mapSchema(
          managedMetalake,
          normalizedCatalogName,
          schema,
          loadOwner(
              client,
              MetadataObjects.of(
                  normalizedCatalogName, schema.name(), MetadataObject.Type.SCHEMA)));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean deleteSchema(
      String metalake, String catalogName, String schemaName, boolean force) {
    return deleteSchema(metalake, catalogName, schemaName, force, null);
  }

  public boolean deleteSchema(
      String metalake,
      String catalogName,
      String schemaName,
      boolean force,
      String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    String normalizedSchemaName = requireName(schemaName, "schemaName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      Catalog catalog = client.loadCatalog(normalizedCatalogName);
      return catalog.asSchemas().dropSchema(normalizedSchemaName, force);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public List<GravitinoTableSummaryResponse> listTables(
      String metalake, String catalogName, String schemaName) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    String normalizedSchemaName = requireName(schemaName, "schemaName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      TableCatalog tableCatalog = client.loadCatalog(normalizedCatalogName).asTableCatalog();
      return Arrays.stream(tableCatalog.listTables(Namespace.of(normalizedSchemaName)))
          .map(
              identifier ->
                  GravitinoDtoMapper.mapTableSummary(
                      managedMetalake,
                      normalizedCatalogName,
                      normalizedSchemaName,
                      identifier.name()))
          .toList();
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoTableResponse createTable(
      String metalake, String catalogName, String schemaName, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    String normalizedSchemaName = requireName(schemaName, "schemaName");
    TableCreateRequest request =
        objectMapper.convertValue(nullSafeBody(body), TableCreateRequest.class);
    request.validate();
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      TableCatalog tableCatalog = client.loadCatalog(normalizedCatalogName).asTableCatalog();
      NameIdentifier identifier =
          NameIdentifier.of(Namespace.of(normalizedSchemaName), request.getName());
      Table table =
          tableCatalog.createTable(
              identifier,
              DTOConverters.fromDTOs(request.getColumns()),
              request.getComment(),
              nullSafeMap(request.getProperties()),
              DTOConverters.fromDTOs(request.getPartitioning()),
              DTOConverters.fromDTO(request.getDistribution()),
              DTOConverters.fromDTOs(request.getSortOrders()),
              DTOConverters.fromDTOs(request.getIndexes()));
      return GravitinoDtoMapper.mapTable(
          managedMetalake,
          normalizedCatalogName,
          normalizedSchemaName,
          table,
          loadOwner(
              client,
              MetadataObjects.of(
                  normalizedCatalogName + "." + normalizedSchemaName,
                  table.name(),
                  MetadataObject.Type.TABLE)));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoTableResponse loadTable(
      String metalake, String catalogName, String schemaName, String tableName) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    String normalizedSchemaName = requireName(schemaName, "schemaName");
    String normalizedTableName = requireName(tableName, "tableName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Table table =
          client
              .loadCatalog(normalizedCatalogName)
              .asTableCatalog()
              .loadTable(
                  NameIdentifier.of(Namespace.of(normalizedSchemaName), normalizedTableName));
      return GravitinoDtoMapper.mapTable(
          managedMetalake,
          normalizedCatalogName,
          normalizedSchemaName,
          table,
          loadOwner(
              client,
              MetadataObjects.of(
                  normalizedCatalogName + "." + normalizedSchemaName,
                  table.name(),
                  MetadataObject.Type.TABLE)));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoTableResponse updateTable(
      String metalake, String catalogName, String schemaName, String tableName, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    String normalizedSchemaName = requireName(schemaName, "schemaName");
    String normalizedTableName = requireName(tableName, "tableName");
    TableChange[] changes = parseTableChanges(nullSafeBody(body));
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Table table =
          client
              .loadCatalog(normalizedCatalogName)
              .asTableCatalog()
              .alterTable(
                  NameIdentifier.of(Namespace.of(normalizedSchemaName), normalizedTableName),
                  changes);
      return GravitinoDtoMapper.mapTable(
          managedMetalake,
          normalizedCatalogName,
          normalizedSchemaName,
          table,
          loadOwner(
              client,
              MetadataObjects.of(
                  normalizedCatalogName + "." + normalizedSchemaName,
                  table.name(),
                  MetadataObject.Type.TABLE)));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean deleteTable(
      String metalake, String catalogName, String schemaName, String tableName, boolean force) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    String normalizedSchemaName = requireName(schemaName, "schemaName");
    String normalizedTableName = requireName(tableName, "tableName");
    NameIdentifier identifier =
        NameIdentifier.of(Namespace.of(normalizedSchemaName), normalizedTableName);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      TableCatalog tableCatalog = client.loadCatalog(normalizedCatalogName).asTableCatalog();
      return force ? tableCatalog.purgeTable(identifier) : tableCatalog.dropTable(identifier);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public List<GravitinoTagSummaryResponse> listTags(String metalake) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return Arrays.stream(client.listTags())
          .sorted()
          .map(tagName -> GravitinoDtoMapper.mapTagSummary(managedMetalake, tagName))
          .toList();
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoTagResponse createTag(String metalake, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    TagCreateRequest request =
        objectMapper.convertValue(nullSafeBody(body), TagCreateRequest.class);
    request.validate();
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Tag tag =
          client.createTag(
              request.getName(), request.getComment(), nullSafeMap(request.getProperties()));
      return GravitinoDtoMapper.mapTag(managedMetalake, tag);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoTagResponse loadTag(String metalake, String tagName) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedTagName = requireName(tagName, "tagName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return GravitinoDtoMapper.mapTag(managedMetalake, client.getTag(normalizedTagName));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoTagResponse updateTag(String metalake, String tagName, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedTagName = requireName(tagName, "tagName");
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      TagChangeWrapper changesWrapper = parseTagChanges(normalizedBody);
      Tag tag =
          changesWrapper.changes().length == 0
              ? client.getTag(normalizedTagName)
              : client.alterTag(normalizedTagName, changesWrapper.changes());
      return GravitinoDtoMapper.mapTag(managedMetalake, tag);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean deleteTag(String metalake, String tagName) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedTagName = requireName(tagName, "tagName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return client.deleteTag(normalizedTagName);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public List<GravitinoTagSummaryResponse> listObjectTags(
      String metalake, String objectType, String fullName) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return mapTagNames(
          managedMetalake, resolveTagTarget(client, objectType, fullName).listTags());
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public List<GravitinoTagSummaryResponse> alterObjectTags(
      String metalake, String objectType, String fullName, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    TagsAssociateRequest request =
        objectMapper.convertValue(nullSafeBody(body), TagsAssociateRequest.class);
    request.validate();
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      String[] tagNames =
          resolveTagTarget(client, objectType, fullName)
              .associateTags(
                  arrayOrEmpty(request.getTagsToAdd()), arrayOrEmpty(request.getTagsToRemove()));
      return mapTagNames(managedMetalake, tagNames);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean dropMetalake(String metalakeName, boolean force, String principalUsername) {
    String normalizedMetalakeName = requireName(metalakeName, "metalakeName");
    try (GravitinoAdminClient client = clientFactory.createAdminClient(principalUsername)) {
      return client.dropMetalake(normalizedMetalakeName, force);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public void setMetalakeOwner(String metalakeName, String ownerName, String principalUsername) {
    String normalizedMetalakeName = requireName(metalakeName, "metalakeName");
    try (GravitinoAdminClient client = clientFactory.createAdminClient(principalUsername)) {
      client
          .loadMetalake(normalizedMetalakeName)
          .setOwner(
              MetadataObjects.of(null, normalizedMetalakeName, MetadataObject.Type.METALAKE),
              requireName(ownerName, "ownerName"),
              Owner.Type.USER);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public void setCatalogOwner(
      String metalake, String catalogName, String ownerName, String principalUsername) {
    setOwner(
        metalake,
        MetadataObjects.of(
            null, requireName(catalogName, "catalogName"), MetadataObject.Type.CATALOG),
        ownerName,
        principalUsername);
  }

  public void setSchemaOwner(
      String metalake,
      String catalogName,
      String schemaName,
      String ownerName,
      String principalUsername) {
    setOwner(
        metalake,
        MetadataObjects.of(
            requireName(catalogName, "catalogName"),
            requireName(schemaName, "schemaName"),
            MetadataObject.Type.SCHEMA),
        ownerName,
        principalUsername);
  }

  private GravitinoCatalogResponse createCatalogInternal(
      String metalake, JsonNode body, String principalUsername, boolean ignoreExisting) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    CatalogCreateRequest request =
        objectMapper.convertValue(nullSafeBody(body), CatalogCreateRequest.class);
    request.validate();
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      Catalog catalog;
      try {
        catalog =
            client.createCatalog(
                request.getName(),
                request.getType(),
                request.getProvider(),
                request.getComment(),
                nullSafeMap(request.getProperties()));
      } catch (CatalogAlreadyExistsException ignored) {
        if (!ignoreExisting) {
          throw ignored;
        }
        catalog = client.loadCatalog(request.getName());
      }
      return GravitinoDtoMapper.mapCatalog(
          managedMetalake,
          catalog,
          loadOwner(client, MetadataObjects.of(null, catalog.name(), MetadataObject.Type.CATALOG)));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  private GravitinoSchemaResponse createSchemaInternal(
      String metalake,
      String catalogName,
      JsonNode body,
      String principalUsername,
      boolean ignoreExisting) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCatalogName = requireName(catalogName, "catalogName");
    SchemaCreateRequest request =
        objectMapper.convertValue(nullSafeBody(body), SchemaCreateRequest.class);
    request.validate();
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      Catalog catalog = client.loadCatalog(normalizedCatalogName);
      Schema schema;
      try {
        schema =
            catalog
                .asSchemas()
                .createSchema(
                    request.getName(), request.getComment(), nullSafeMap(request.getProperties()));
      } catch (SchemaAlreadyExistsException ignored) {
        if (!ignoreExisting) {
          throw ignored;
        }
        schema = catalog.asSchemas().loadSchema(request.getName());
      }
      return GravitinoDtoMapper.mapSchema(
          managedMetalake,
          normalizedCatalogName,
          schema,
          loadOwner(
              client,
              MetadataObjects.of(
                  normalizedCatalogName, schema.name(), MetadataObject.Type.SCHEMA)));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  private void setOwner(
      String metalake, MetadataObject metadataObject, String ownerName, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      client.setOwner(metadataObject, requireName(ownerName, "ownerName"), Owner.Type.USER);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  private GravitinoOwnerResponse loadOwner(GravitinoClient client, MetadataObject object) {
    return GravitinoDtoMapper.mapOwner(client.getOwner(object));
  }

  private List<GravitinoTagSummaryResponse> mapTagNames(String metalake, String[] tagNames) {
    if (tagNames == null || tagNames.length == 0) {
      return List.of();
    }
    return Arrays.stream(tagNames)
        .sorted()
        .map(tagName -> GravitinoDtoMapper.mapTagSummary(metalake, tagName))
        .toList();
  }

  private SupportsTags resolveTagTarget(
      GravitinoClient client, String objectType, String fullName) {
    String normalizedType = objectType == null ? "" : objectType.trim().toUpperCase(Locale.ROOT);
    String[] parts = splitFullName(fullName);
    return switch (normalizedType) {
      case "CATALOG" -> {
        if (parts.length != 1) {
          throw badRequest("Catalog fullName must be catalogName");
        }
        yield client.loadCatalog(parts[0]).supportsTags();
      }
      case "SCHEMA" -> {
        if (parts.length < 2) {
          throw badRequest("Schema fullName must be catalog.schema");
        }
        Catalog catalog = client.loadCatalog(parts[0]);
        String schemaName = String.join(".", Arrays.copyOfRange(parts, 1, parts.length));
        yield catalog.asSchemas().loadSchema(schemaName).supportsTags();
      }
      case "TABLE" -> loadTable(client, parts).supportsTags();
      case "COLUMN" -> {
        if (parts.length < 4) {
          throw badRequest("Column fullName must be catalog.schema.table.column");
        }
        String[] tableParts = Arrays.copyOf(parts, parts.length - 1);
        yield resolveColumnTag(loadTable(client, tableParts), parts[parts.length - 1]);
      }
      default -> throw badRequest("Unsupported objectType for tag operations: " + objectType);
    };
  }

  private Table loadTable(GravitinoClient client, String[] fullNameParts) {
    if (fullNameParts.length < 3) {
      throw badRequest("Table fullName must be catalog.schema.table");
    }
    Catalog catalog = client.loadCatalog(fullNameParts[0]);
    TableCatalog tableCatalog = catalog.asTableCatalog();
    String[] schemaParts = Arrays.copyOfRange(fullNameParts, 1, fullNameParts.length - 1);
    String tableName = fullNameParts[fullNameParts.length - 1];
    return tableCatalog.loadTable(NameIdentifier.of(Namespace.of(schemaParts), tableName));
  }

  private SupportsTags resolveColumnTag(Table table, String columnName) {
    return Arrays.stream(table.columns())
        .filter(column -> column.name().equals(columnName))
        .findFirst()
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Column not found for metadata object tag operation: %s", columnName))
        .supportsTags();
  }

  private CatalogChange[] parseCatalogChanges(JsonNode body, Catalog currentCatalog) {
    if (body.has("updates")) {
      CatalogUpdatesRequest updatesRequest =
          objectMapper.convertValue(body, CatalogUpdatesRequest.class);
      if (updatesRequest.getUpdates() == null || updatesRequest.getUpdates().isEmpty()) {
        throw badRequest("updates must be a non-empty array");
      }
      List<CatalogChange> changes = new ArrayList<>();
      for (CatalogUpdateRequest update : updatesRequest.getUpdates()) {
        update.validate();
        changes.add(update.catalogChange());
      }
      return changes.toArray(new CatalogChange[0]);
    }

    List<CatalogChange> changes = new ArrayList<>();
    if (body.has("comment")) {
      changes.add(CatalogChange.updateComment(nullableTextAllowBlank(body, "comment")));
    }
    if (body.has("properties")) {
      Map<String, String> currentProperties = nullSafeMap(currentCatalog.properties());
      Map<String, String> targetProperties = parseStringMap(body.get("properties"));
      collectMapDiffCatalogChanges(changes, currentProperties, targetProperties);
    }
    return changes.toArray(new CatalogChange[0]);
  }

  private SchemaChange[] parseSchemaChanges(JsonNode body, Schema currentSchema) {
    if (body.has("updates")) {
      SchemaUpdatesRequest updatesRequest =
          objectMapper.convertValue(body, SchemaUpdatesRequest.class);
      if (updatesRequest.getUpdates() == null || updatesRequest.getUpdates().isEmpty()) {
        throw badRequest("updates must be a non-empty array");
      }
      List<SchemaChange> changes = new ArrayList<>();
      for (SchemaUpdateRequest update : updatesRequest.getUpdates()) {
        update.validate();
        changes.add(update.schemaChange());
      }
      return changes.toArray(new SchemaChange[0]);
    }

    if (!body.has("properties")) {
      return new SchemaChange[0];
    }
    Map<String, String> currentProperties = nullSafeMap(currentSchema.properties());
    Map<String, String> targetProperties = parseStringMap(body.get("properties"));
    List<SchemaChange> changes = new ArrayList<>();
    collectMapDiffSchemaChanges(changes, currentProperties, targetProperties);
    return changes.toArray(new SchemaChange[0]);
  }

  private TableChange[] parseTableChanges(JsonNode body) {
    TableUpdatesRequest updatesRequest = objectMapper.convertValue(body, TableUpdatesRequest.class);
    if (updatesRequest.getUpdates() == null || updatesRequest.getUpdates().isEmpty()) {
      throw badRequest("updates must be a non-empty array");
    }
    List<TableChange> changes = new ArrayList<>();
    for (TableUpdateRequest update : updatesRequest.getUpdates()) {
      update.validate();
      changes.add(update.tableChange());
    }
    return changes.toArray(new TableChange[0]);
  }

  private TagChangeWrapper parseTagChanges(JsonNode body) {
    if (body.has("updates")) {
      TagUpdatesRequest updatesRequest = objectMapper.convertValue(body, TagUpdatesRequest.class);
      updatesRequest.validate();
      List<org.apache.gravitino.tag.TagChange> changes = new ArrayList<>();
      for (TagUpdateRequest update : updatesRequest.getUpdates()) {
        changes.add(update.tagChange());
      }
      return new TagChangeWrapper(changes.toArray(new org.apache.gravitino.tag.TagChange[0]));
    }

    List<org.apache.gravitino.tag.TagChange> changes = new ArrayList<>();
    if (body.has("comment")) {
      changes.add(
          org.apache.gravitino.tag.TagChange.updateComment(
              nullableTextAllowBlank(body, "comment")));
    }
    if (body.has("properties")) {
      Map<String, String> targetProperties = parseStringMap(body.get("properties"));
      for (Map.Entry<String, String> entry : targetProperties.entrySet()) {
        changes.add(
            org.apache.gravitino.tag.TagChange.setProperty(entry.getKey(), entry.getValue()));
      }
    }
    return new TagChangeWrapper(changes.toArray(new org.apache.gravitino.tag.TagChange[0]));
  }

  private void collectMapDiffCatalogChanges(
      List<CatalogChange> target, Map<String, String> current, Map<String, String> changed) {
    for (Map.Entry<String, String> entry : changed.entrySet()) {
      if (!entry.getValue().equals(current.get(entry.getKey()))) {
        target.add(CatalogChange.setProperty(entry.getKey(), entry.getValue()));
      }
    }
    for (String key : current.keySet()) {
      if (!changed.containsKey(key)) {
        target.add(CatalogChange.removeProperty(key));
      }
    }
  }

  private void collectMapDiffSchemaChanges(
      List<SchemaChange> target, Map<String, String> current, Map<String, String> changed) {
    for (Map.Entry<String, String> entry : changed.entrySet()) {
      if (!entry.getValue().equals(current.get(entry.getKey()))) {
        target.add(SchemaChange.setProperty(entry.getKey(), entry.getValue()));
      }
    }
    for (String key : current.keySet()) {
      if (!changed.containsKey(key)) {
        target.add(SchemaChange.removeProperty(key));
      }
    }
  }

  private JsonNode nullSafeBody(JsonNode body) {
    return body == null || body.isNull() ? objectMapper.createObjectNode() : body;
  }

  private String requireName(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw badRequest(fieldName + " must not be blank");
    }
    return value.trim();
  }

  private String nullableTextAllowBlank(JsonNode node, String field) {
    JsonNode valueNode = node == null ? null : node.get(field);
    if (valueNode == null || valueNode.isNull()) {
      return null;
    }
    return valueNode.asText();
  }

  private String[] splitFullName(String fullName) {
    if (fullName == null || fullName.isBlank()) {
      throw badRequest("fullName must not be blank");
    }
    String[] parts =
        Arrays.stream(fullName.trim().split("\\."))
            .filter(part -> part != null && !part.isBlank())
            .toArray(String[]::new);
    if (parts.length == 0) {
      throw badRequest("fullName must not be blank");
    }
    return parts;
  }

  private Map<String, String> parseStringMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Collections.emptyMap();
    }
    if (!node.isObject()) {
      throw badRequest("properties must be an object");
    }
    return objectMapper.convertValue(
        node,
        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
  }

  private Map<String, String> nullSafeMap(Map<String, String> properties) {
    return properties == null ? Map.of() : properties;
  }

  private String[] arrayOrEmpty(String[] values) {
    return values == null ? new String[0] : values;
  }

  private BadRequestException badRequest(String message) {
    return new BadRequestException(message);
  }

  private record TagChangeWrapper(org.apache.gravitino.tag.TagChange[] changes) {}
}
