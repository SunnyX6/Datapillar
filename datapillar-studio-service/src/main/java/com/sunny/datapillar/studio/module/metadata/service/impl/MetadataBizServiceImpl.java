package com.sunny.datapillar.studio.module.metadata.service.impl;

import com.sunny.datapillar.studio.dto.metadata.request.CatalogCreateRequest;
import com.sunny.datapillar.studio.dto.metadata.request.CatalogTestConnectionRequest;
import com.sunny.datapillar.studio.dto.metadata.request.CatalogUpdateRequest;
import com.sunny.datapillar.studio.dto.metadata.request.ObjectTagAlterRequest;
import com.sunny.datapillar.studio.dto.metadata.request.SchemaCreateRequest;
import com.sunny.datapillar.studio.dto.metadata.request.SchemaUpdateRequest;
import com.sunny.datapillar.studio.dto.metadata.request.TableCreateRequest;
import com.sunny.datapillar.studio.dto.metadata.request.TableUpdateRequest;
import com.sunny.datapillar.studio.dto.metadata.request.TagCreateRequest;
import com.sunny.datapillar.studio.dto.metadata.request.TagUpdateRequest;
import com.sunny.datapillar.studio.dto.metadata.response.CatalogResponse;
import com.sunny.datapillar.studio.dto.metadata.response.CatalogSummaryResponse;
import com.sunny.datapillar.studio.dto.metadata.response.OwnerResponse;
import com.sunny.datapillar.studio.dto.metadata.response.SchemaResponse;
import com.sunny.datapillar.studio.dto.metadata.response.SchemaSummaryResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TableResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TableSummaryResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TagResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TagSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoCatalogService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoOwnerService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoSchemaService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoTableService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoTagService;
import com.sunny.datapillar.studio.module.metadata.mapper.MetadataDtoMapper;
import com.sunny.datapillar.studio.module.metadata.service.MetadataBizService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetadataBizServiceImpl implements MetadataBizService {

  private final GravitinoCatalogService catalogService;
  private final GravitinoSchemaService schemaService;
  private final GravitinoTableService tableService;
  private final GravitinoTagService tagService;
  private final GravitinoOwnerService ownerService;
  private final MetadataDtoMapper metadataDtoMapper;

  @Override
  public List<CatalogSummaryResponse> listCatalogs() {
    return metadataDtoMapper.toCatalogSummaryResponses(catalogService.listCatalogs());
  }

  @Override
  public void testCatalogConnection(CatalogTestConnectionRequest request) {
    catalogService.testCatalogConnection(metadataDtoMapper.toCatalogTestConnectionCommand(request));
  }

  @Override
  public CatalogResponse createCatalog(CatalogCreateRequest request) {
    return metadataDtoMapper.toCatalogResponse(
        catalogService.createCatalog(metadataDtoMapper.toCatalogCreateCommand(request)));
  }

  @Override
  public CatalogResponse loadCatalog(String catalogName) {
    return metadataDtoMapper.toCatalogResponse(catalogService.loadCatalog(catalogName));
  }

  @Override
  public CatalogResponse updateCatalog(String catalogName, CatalogUpdateRequest request) {
    return metadataDtoMapper.toCatalogResponse(
        catalogService.updateCatalog(
            catalogName, metadataDtoMapper.toCatalogUpdateCommand(request)));
  }

  @Override
  public boolean deleteCatalog(String catalogName, boolean force) {
    return catalogService.deleteCatalog(catalogName, force);
  }

  @Override
  public List<SchemaSummaryResponse> listSchemas(String catalogName) {
    return metadataDtoMapper.toSchemaSummaryResponses(schemaService.listSchemas(catalogName));
  }

  @Override
  public SchemaResponse createSchema(String catalogName, SchemaCreateRequest request) {
    return metadataDtoMapper.toSchemaResponse(
        schemaService.createSchema(catalogName, metadataDtoMapper.toSchemaCreateCommand(request)));
  }

  @Override
  public SchemaResponse loadSchema(String catalogName, String schemaName) {
    return metadataDtoMapper.toSchemaResponse(schemaService.loadSchema(catalogName, schemaName));
  }

  @Override
  public SchemaResponse updateSchema(
      String catalogName, String schemaName, SchemaUpdateRequest request) {
    return metadataDtoMapper.toSchemaResponse(
        schemaService.updateSchema(
            catalogName, schemaName, metadataDtoMapper.toSchemaUpdateCommand(request)));
  }

  @Override
  public boolean deleteSchema(String catalogName, String schemaName, boolean force) {
    return schemaService.deleteSchema(catalogName, schemaName, force);
  }

  @Override
  public List<TableSummaryResponse> listTables(String catalogName, String schemaName) {
    return metadataDtoMapper.toTableSummaryResponses(
        tableService.listTables(catalogName, schemaName));
  }

  @Override
  public TableResponse createTable(
      String catalogName, String schemaName, TableCreateRequest request) {
    return metadataDtoMapper.toTableResponse(
        tableService.createTable(
            catalogName, schemaName, metadataDtoMapper.toTableCreateCommand(request)));
  }

  @Override
  public TableResponse loadTable(String catalogName, String schemaName, String tableName) {
    return metadataDtoMapper.toTableResponse(
        tableService.loadTable(catalogName, schemaName, tableName));
  }

  @Override
  public TableResponse updateTable(
      String catalogName, String schemaName, String tableName, TableUpdateRequest request) {
    return metadataDtoMapper.toTableResponse(
        tableService.updateTable(
            catalogName, schemaName, tableName, metadataDtoMapper.toTableUpdateCommand(request)));
  }

  @Override
  public boolean deleteTable(
      String catalogName, String schemaName, String tableName, boolean force) {
    return tableService.deleteTable(catalogName, schemaName, tableName, force);
  }

  @Override
  public List<TagSummaryResponse> listTags() {
    return metadataDtoMapper.toTagSummaryResponses(tagService.listTags());
  }

  @Override
  public TagResponse createTag(TagCreateRequest request) {
    return metadataDtoMapper.toTagResponse(
        tagService.createTag(metadataDtoMapper.toTagCreateCommand(request)));
  }

  @Override
  public TagResponse loadTag(String tagName) {
    return metadataDtoMapper.toTagResponse(tagService.loadTag(tagName));
  }

  @Override
  public TagResponse updateTag(String tagName, TagUpdateRequest request) {
    return metadataDtoMapper.toTagResponse(
        tagService.updateTag(tagName, metadataDtoMapper.toTagUpdateCommand(request)));
  }

  @Override
  public boolean deleteTag(String tagName) {
    return tagService.deleteTag(tagName);
  }

  @Override
  public List<TagSummaryResponse> listObjectTags(String objectType, String fullName) {
    return metadataDtoMapper.toTagSummaryResponses(
        tagService.listObjectTags(
            GravitinoDomainRoutingService.DOMAIN_METADATA, objectType, fullName));
  }

  @Override
  public List<TagSummaryResponse> alterObjectTags(
      String objectType, String fullName, ObjectTagAlterRequest request) {
    return metadataDtoMapper.toTagSummaryResponses(
        tagService.alterObjectTags(
            GravitinoDomainRoutingService.DOMAIN_METADATA,
            objectType,
            fullName,
            metadataDtoMapper.toObjectTagAlterCommand(request)));
  }

  @Override
  public OwnerResponse getOwner(String objectType, String fullName) {
    return metadataDtoMapper.toOwnerResponse(
        ownerService.getOwner(GravitinoDomainRoutingService.DOMAIN_METADATA, objectType, fullName));
  }
}
