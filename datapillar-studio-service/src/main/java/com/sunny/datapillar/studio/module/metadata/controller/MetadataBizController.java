package com.sunny.datapillar.studio.module.metadata.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.dto.metadata.response.CatalogResponse;
import com.sunny.datapillar.studio.dto.metadata.response.CatalogSummaryResponse;
import com.sunny.datapillar.studio.dto.metadata.response.OwnerResponse;
import com.sunny.datapillar.studio.dto.metadata.response.SchemaResponse;
import com.sunny.datapillar.studio.dto.metadata.response.SchemaSummaryResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TableResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TableSummaryResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TagResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TagSummaryResponse;
import com.sunny.datapillar.studio.module.metadata.service.MetadataBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Metadata", description = "Metadata business interface")
@RestController
@RequestMapping("/biz/metadata")
@RequiredArgsConstructor
public class MetadataBizController {

  private final MetadataBizService metadataBizService;

  @Operation(summary = "List catalogs")
  @GetMapping("/catalogs")
  public ApiResponse<List<CatalogSummaryResponse>> listCatalogs() {
    return ApiResponse.ok(metadataBizService.listCatalogs());
  }

  @Operation(summary = "Load catalog")
  @GetMapping("/catalogs/{catalogName}")
  public ApiResponse<CatalogResponse> loadCatalog(@PathVariable String catalogName) {
    return ApiResponse.ok(metadataBizService.loadCatalog(catalogName));
  }

  @Operation(summary = "List schemas")
  @GetMapping("/catalogs/{catalogName}/schemas")
  public ApiResponse<List<SchemaSummaryResponse>> listSchemas(@PathVariable String catalogName) {
    return ApiResponse.ok(metadataBizService.listSchemas(catalogName));
  }

  @Operation(summary = "Load schema")
  @GetMapping("/catalogs/{catalogName}/schemas/{schemaName}")
  public ApiResponse<SchemaResponse> loadSchema(
      @PathVariable String catalogName, @PathVariable String schemaName) {
    return ApiResponse.ok(metadataBizService.loadSchema(catalogName, schemaName));
  }

  @Operation(summary = "List tables")
  @GetMapping("/catalogs/{catalogName}/schemas/{schemaName}/tables")
  public ApiResponse<List<TableSummaryResponse>> listTables(
      @PathVariable String catalogName, @PathVariable String schemaName) {
    return ApiResponse.ok(metadataBizService.listTables(catalogName, schemaName));
  }

  @Operation(summary = "Load table")
  @GetMapping("/catalogs/{catalogName}/schemas/{schemaName}/tables/{tableName}")
  public ApiResponse<TableResponse> loadTable(
      @PathVariable String catalogName,
      @PathVariable String schemaName,
      @PathVariable String tableName) {
    return ApiResponse.ok(metadataBizService.loadTable(catalogName, schemaName, tableName));
  }

  @Operation(summary = "List tags")
  @GetMapping("/tags")
  public ApiResponse<List<TagSummaryResponse>> listTags() {
    return ApiResponse.ok(metadataBizService.listTags());
  }

  @Operation(summary = "Load tag")
  @GetMapping("/tags/{tagName}")
  public ApiResponse<TagResponse> loadTag(@PathVariable String tagName) {
    return ApiResponse.ok(metadataBizService.loadTag(tagName));
  }

  @Operation(summary = "List object tags")
  @GetMapping("/objects/{objectType}/{fullName}/tags")
  public ApiResponse<List<TagSummaryResponse>> listObjectTags(
      @PathVariable String objectType, @PathVariable String fullName) {
    return ApiResponse.ok(metadataBizService.listObjectTags(objectType, fullName));
  }

  @Operation(summary = "Get owner")
  @GetMapping("/owners/{objectType}/{fullName}")
  public ApiResponse<OwnerResponse> getOwner(
      @PathVariable String objectType, @PathVariable String fullName) {
    return ApiResponse.ok(metadataBizService.getOwner(objectType, fullName));
  }
}
