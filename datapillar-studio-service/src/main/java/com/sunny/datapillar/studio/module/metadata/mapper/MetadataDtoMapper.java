package com.sunny.datapillar.studio.module.metadata.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoOwnerResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogTestConnectionCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ObjectTagAlterCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.SchemaCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.SchemaUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TableCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TableUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TagCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TagUpdateCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataDtoMapper {

  private final ObjectMapper objectMapper;

  public CatalogTestConnectionCommand toCatalogTestConnectionCommand(
      CatalogTestConnectionRequest request) {
    return convert(request, CatalogTestConnectionCommand.class);
  }

  public CatalogCreateCommand toCatalogCreateCommand(CatalogCreateRequest request) {
    return convert(request, CatalogCreateCommand.class);
  }

  public CatalogUpdateCommand toCatalogUpdateCommand(CatalogUpdateRequest request) {
    return convert(request, CatalogUpdateCommand.class);
  }

  public SchemaCreateCommand toSchemaCreateCommand(SchemaCreateRequest request) {
    return convert(request, SchemaCreateCommand.class);
  }

  public SchemaUpdateCommand toSchemaUpdateCommand(SchemaUpdateRequest request) {
    return convert(request, SchemaUpdateCommand.class);
  }

  public TableCreateCommand toTableCreateCommand(TableCreateRequest request) {
    return convert(request, TableCreateCommand.class);
  }

  public TableUpdateCommand toTableUpdateCommand(TableUpdateRequest request) {
    return convert(request, TableUpdateCommand.class);
  }

  public TagCreateCommand toTagCreateCommand(TagCreateRequest request) {
    return convert(request, TagCreateCommand.class);
  }

  public TagUpdateCommand toTagUpdateCommand(TagUpdateRequest request) {
    return convert(request, TagUpdateCommand.class);
  }

  public ObjectTagAlterCommand toObjectTagAlterCommand(ObjectTagAlterRequest request) {
    return convert(request, ObjectTagAlterCommand.class);
  }

  public CatalogResponse toCatalogResponse(GravitinoCatalogResponse response) {
    return convert(response, CatalogResponse.class);
  }

  public List<CatalogSummaryResponse> toCatalogSummaryResponses(
      List<GravitinoCatalogSummaryResponse> responses) {
    return convertList(responses, CatalogSummaryResponse.class);
  }

  public SchemaResponse toSchemaResponse(GravitinoSchemaResponse response) {
    return convert(response, SchemaResponse.class);
  }

  public List<SchemaSummaryResponse> toSchemaSummaryResponses(
      List<GravitinoSchemaSummaryResponse> responses) {
    return convertList(responses, SchemaSummaryResponse.class);
  }

  public TableResponse toTableResponse(GravitinoTableResponse response) {
    return convert(response, TableResponse.class);
  }

  public List<TableSummaryResponse> toTableSummaryResponses(
      List<GravitinoTableSummaryResponse> responses) {
    return convertList(responses, TableSummaryResponse.class);
  }

  public TagResponse toTagResponse(GravitinoTagResponse response) {
    return convert(response, TagResponse.class);
  }

  public List<TagSummaryResponse> toTagSummaryResponses(
      List<GravitinoTagSummaryResponse> responses) {
    return convertList(responses, TagSummaryResponse.class);
  }

  public OwnerResponse toOwnerResponse(GravitinoOwnerResponse response) {
    return convert(response, OwnerResponse.class);
  }

  private <T> T convert(Object source, Class<T> targetType) {
    if (source == null) {
      return null;
    }
    return objectMapper.convertValue(source, targetType);
  }

  private <S, T> List<T> convertList(List<S> sources, Class<T> targetType) {
    if (sources == null || sources.isEmpty()) {
      return List.of();
    }
    return sources.stream().map(source -> convert(source, targetType)).toList();
  }
}
