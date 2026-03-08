package com.sunny.datapillar.studio.module.metadata.controller;

import com.sunny.datapillar.common.response.ApiResponse;
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
import com.sunny.datapillar.studio.dto.metadata.response.SchemaResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TableResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TagResponse;
import com.sunny.datapillar.studio.dto.metadata.response.TagSummaryResponse;
import com.sunny.datapillar.studio.module.metadata.service.MetadataBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Metadata admin", description = "Metadata admin interface")
@RestController
@RequestMapping("/admin/metadata")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class MetadataAdminController {

  private final MetadataBizService metadataBizService;

  @Operation(summary = "Test catalog connection")
  @PostMapping("/catalogs/testConnection")
  public ApiResponse<Void> testCatalogConnection(
      @Valid @RequestBody CatalogTestConnectionRequest request) {
    metadataBizService.testCatalogConnection(request);
    return ApiResponse.ok();
  }

  @Operation(summary = "Create catalog")
  @PostMapping("/catalogs")
  public ApiResponse<CatalogResponse> createCatalog(
      @Valid @RequestBody CatalogCreateRequest request) {
    return ApiResponse.ok(metadataBizService.createCatalog(request));
  }

  @Operation(summary = "Update catalog")
  @PutMapping("/catalogs/{catalogName}")
  public ApiResponse<CatalogResponse> updateCatalog(
      @PathVariable String catalogName, @Valid @RequestBody CatalogUpdateRequest request) {
    return ApiResponse.ok(metadataBizService.updateCatalog(catalogName, request));
  }

  @Operation(summary = "Delete catalog")
  @DeleteMapping("/catalogs/{catalogName}")
  public ApiResponse<Boolean> deleteCatalog(
      @PathVariable String catalogName, @RequestParam(defaultValue = "false") boolean force) {
    return ApiResponse.ok(metadataBizService.deleteCatalog(catalogName, force));
  }

  @Operation(summary = "Create schema")
  @PostMapping("/catalogs/{catalogName}/schemas")
  public ApiResponse<SchemaResponse> createSchema(
      @PathVariable String catalogName, @Valid @RequestBody SchemaCreateRequest request) {
    return ApiResponse.ok(metadataBizService.createSchema(catalogName, request));
  }

  @Operation(summary = "Update schema")
  @PutMapping("/catalogs/{catalogName}/schemas/{schemaName}")
  public ApiResponse<SchemaResponse> updateSchema(
      @PathVariable String catalogName,
      @PathVariable String schemaName,
      @Valid @RequestBody SchemaUpdateRequest request) {
    return ApiResponse.ok(metadataBizService.updateSchema(catalogName, schemaName, request));
  }

  @Operation(summary = "Delete schema")
  @DeleteMapping("/catalogs/{catalogName}/schemas/{schemaName}")
  public ApiResponse<Boolean> deleteSchema(
      @PathVariable String catalogName,
      @PathVariable String schemaName,
      @RequestParam(defaultValue = "false") boolean force) {
    return ApiResponse.ok(metadataBizService.deleteSchema(catalogName, schemaName, force));
  }

  @Operation(summary = "Create table")
  @PostMapping("/catalogs/{catalogName}/schemas/{schemaName}/tables")
  public ApiResponse<TableResponse> createTable(
      @PathVariable String catalogName,
      @PathVariable String schemaName,
      @Valid @RequestBody TableCreateRequest request) {
    return ApiResponse.ok(metadataBizService.createTable(catalogName, schemaName, request));
  }

  @Operation(summary = "Update table")
  @PutMapping("/catalogs/{catalogName}/schemas/{schemaName}/tables/{tableName}")
  public ApiResponse<TableResponse> updateTable(
      @PathVariable String catalogName,
      @PathVariable String schemaName,
      @PathVariable String tableName,
      @Valid @RequestBody TableUpdateRequest request) {
    return ApiResponse.ok(
        metadataBizService.updateTable(catalogName, schemaName, tableName, request));
  }

  @Operation(summary = "Delete table")
  @DeleteMapping("/catalogs/{catalogName}/schemas/{schemaName}/tables/{tableName}")
  public ApiResponse<Boolean> deleteTable(
      @PathVariable String catalogName,
      @PathVariable String schemaName,
      @PathVariable String tableName,
      @RequestParam(defaultValue = "false") boolean force) {
    return ApiResponse.ok(
        metadataBizService.deleteTable(catalogName, schemaName, tableName, force));
  }

  @Operation(summary = "Create tag")
  @PostMapping("/tags")
  public ApiResponse<TagResponse> createTag(@Valid @RequestBody TagCreateRequest request) {
    return ApiResponse.ok(metadataBizService.createTag(request));
  }

  @Operation(summary = "Update tag")
  @PutMapping("/tags/{tagName}")
  public ApiResponse<TagResponse> updateTag(
      @PathVariable String tagName, @Valid @RequestBody TagUpdateRequest request) {
    return ApiResponse.ok(metadataBizService.updateTag(tagName, request));
  }

  @Operation(summary = "Delete tag")
  @DeleteMapping("/tags/{tagName}")
  public ApiResponse<Boolean> deleteTag(@PathVariable String tagName) {
    return ApiResponse.ok(metadataBizService.deleteTag(tagName));
  }

  @Operation(summary = "Alter object tags")
  @PostMapping("/objects/{objectType}/{fullName}/tags")
  public ApiResponse<List<TagSummaryResponse>> alterObjectTags(
      @PathVariable String objectType,
      @PathVariable String fullName,
      @Valid @RequestBody ObjectTagAlterRequest request) {
    return ApiResponse.ok(metadataBizService.alterObjectTags(objectType, fullName, request));
  }
}
