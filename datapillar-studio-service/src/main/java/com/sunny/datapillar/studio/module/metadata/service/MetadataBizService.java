package com.sunny.datapillar.studio.module.metadata.service;

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
import java.util.List;

public interface MetadataBizService {

  List<CatalogSummaryResponse> listCatalogs();

  void testCatalogConnection(CatalogTestConnectionRequest request);

  CatalogResponse createCatalog(CatalogCreateRequest request);

  CatalogResponse loadCatalog(String catalogName);

  CatalogResponse updateCatalog(String catalogName, CatalogUpdateRequest request);

  boolean deleteCatalog(String catalogName, boolean force);

  List<SchemaSummaryResponse> listSchemas(String catalogName);

  SchemaResponse createSchema(String catalogName, SchemaCreateRequest request);

  SchemaResponse loadSchema(String catalogName, String schemaName);

  SchemaResponse updateSchema(String catalogName, String schemaName, SchemaUpdateRequest request);

  boolean deleteSchema(String catalogName, String schemaName, boolean force);

  List<TableSummaryResponse> listTables(String catalogName, String schemaName);

  TableResponse createTable(String catalogName, String schemaName, TableCreateRequest request);

  TableResponse loadTable(String catalogName, String schemaName, String tableName);

  TableResponse updateTable(
      String catalogName, String schemaName, String tableName, TableUpdateRequest request);

  boolean deleteTable(String catalogName, String schemaName, String tableName, boolean force);

  List<TagSummaryResponse> listTags();

  TagResponse createTag(TagCreateRequest request);

  TagResponse loadTag(String tagName);

  TagResponse updateTag(String tagName, TagUpdateRequest request);

  boolean deleteTag(String tagName);

  List<TagSummaryResponse> listObjectTags(String objectType, String fullName);

  List<TagSummaryResponse> alterObjectTags(
      String objectType, String fullName, ObjectTagAlterRequest request);

  OwnerResponse getOwner(String objectType, String fullName);
}
