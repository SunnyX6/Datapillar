package com.sunny.datapillar.studio.module.semantic.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.dto.semantic.request.MetricCreateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.MetricUpdateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.MetricVersionUpdateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.ModifierCreateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.ModifierUpdateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.ObjectTagAlterRequest;
import com.sunny.datapillar.studio.dto.semantic.request.UnitCreateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.UnitUpdateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.ValueDomainCreateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.ValueDomainUpdateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.WordRootCreateRequest;
import com.sunny.datapillar.studio.dto.semantic.request.WordRootUpdateRequest;
import com.sunny.datapillar.studio.dto.semantic.response.MetricResponse;
import com.sunny.datapillar.studio.dto.semantic.response.MetricSummaryResponse;
import com.sunny.datapillar.studio.dto.semantic.response.MetricVersionResponse;
import com.sunny.datapillar.studio.dto.semantic.response.MetricVersionSummaryResponse;
import com.sunny.datapillar.studio.dto.semantic.response.ModifierResponse;
import com.sunny.datapillar.studio.dto.semantic.response.ModifierSummaryResponse;
import com.sunny.datapillar.studio.dto.semantic.response.OwnerResponse;
import com.sunny.datapillar.studio.dto.semantic.response.PageResponse;
import com.sunny.datapillar.studio.dto.semantic.response.TagSummaryResponse;
import com.sunny.datapillar.studio.dto.semantic.response.UnitResponse;
import com.sunny.datapillar.studio.dto.semantic.response.UnitSummaryResponse;
import com.sunny.datapillar.studio.dto.semantic.response.ValueDomainResponse;
import com.sunny.datapillar.studio.dto.semantic.response.ValueDomainSummaryResponse;
import com.sunny.datapillar.studio.dto.semantic.response.WordRootResponse;
import com.sunny.datapillar.studio.dto.semantic.response.WordRootSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoOwnerResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.MetricCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.MetricUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.MetricVersionUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ModifierCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ModifierUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ObjectTagAlterCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.UnitCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.UnitUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ValueDomainCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ValueDomainUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.WordRootCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.WordRootUpdateCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SemanticDtoMapper {

  private final ObjectMapper objectMapper;

  public WordRootCreateCommand toWordRootCreateCommand(WordRootCreateRequest request) {
    return convert(request, WordRootCreateCommand.class);
  }

  public WordRootUpdateCommand toWordRootUpdateCommand(WordRootUpdateRequest request) {
    return convert(request, WordRootUpdateCommand.class);
  }

  public MetricCreateCommand toMetricCreateCommand(MetricCreateRequest request) {
    return convert(request, MetricCreateCommand.class);
  }

  public MetricUpdateCommand toMetricUpdateCommand(MetricUpdateRequest request) {
    return convert(request, MetricUpdateCommand.class);
  }

  public MetricVersionUpdateCommand toMetricVersionUpdateCommand(
      MetricVersionUpdateRequest request) {
    return convert(request, MetricVersionUpdateCommand.class);
  }

  public UnitCreateCommand toUnitCreateCommand(UnitCreateRequest request) {
    return convert(request, UnitCreateCommand.class);
  }

  public UnitUpdateCommand toUnitUpdateCommand(UnitUpdateRequest request) {
    return convert(request, UnitUpdateCommand.class);
  }

  public ModifierCreateCommand toModifierCreateCommand(ModifierCreateRequest request) {
    return convert(request, ModifierCreateCommand.class);
  }

  public ModifierUpdateCommand toModifierUpdateCommand(ModifierUpdateRequest request) {
    return convert(request, ModifierUpdateCommand.class);
  }

  public ValueDomainCreateCommand toValueDomainCreateCommand(ValueDomainCreateRequest request) {
    return convert(request, ValueDomainCreateCommand.class);
  }

  public ValueDomainUpdateCommand toValueDomainUpdateCommand(ValueDomainUpdateRequest request) {
    return convert(request, ValueDomainUpdateCommand.class);
  }

  public ObjectTagAlterCommand toObjectTagAlterCommand(ObjectTagAlterRequest request) {
    return convert(request, ObjectTagAlterCommand.class);
  }

  public PageResponse<WordRootSummaryResponse> toWordRootPage(
      GravitinoPageResult<GravitinoWordRootSummaryResponse> page) {
    return convertPage(page, WordRootSummaryResponse.class);
  }

  public WordRootResponse toWordRootResponse(GravitinoWordRootResponse response) {
    return convert(response, WordRootResponse.class);
  }

  public PageResponse<MetricSummaryResponse> toMetricPage(
      GravitinoPageResult<GravitinoMetricSummaryResponse> page) {
    return convertPage(page, MetricSummaryResponse.class);
  }

  public MetricResponse toMetricResponse(GravitinoMetricResponse response) {
    return convert(response, MetricResponse.class);
  }

  public List<MetricVersionSummaryResponse> toMetricVersionSummaryResponses(
      List<GravitinoMetricVersionSummaryResponse> responses) {
    return convertList(responses, MetricVersionSummaryResponse.class);
  }

  public MetricVersionResponse toMetricVersionResponse(GravitinoMetricVersionResponse response) {
    return convert(response, MetricVersionResponse.class);
  }

  public PageResponse<UnitSummaryResponse> toUnitPage(
      GravitinoPageResult<GravitinoUnitSummaryResponse> page) {
    return convertPage(page, UnitSummaryResponse.class);
  }

  public UnitResponse toUnitResponse(GravitinoUnitResponse response) {
    return convert(response, UnitResponse.class);
  }

  public PageResponse<ModifierSummaryResponse> toModifierPage(
      GravitinoPageResult<GravitinoModifierSummaryResponse> page) {
    return convertPage(page, ModifierSummaryResponse.class);
  }

  public ModifierResponse toModifierResponse(GravitinoModifierResponse response) {
    return convert(response, ModifierResponse.class);
  }

  public PageResponse<ValueDomainSummaryResponse> toValueDomainPage(
      GravitinoPageResult<GravitinoValueDomainSummaryResponse> page) {
    return convertPage(page, ValueDomainSummaryResponse.class);
  }

  public ValueDomainResponse toValueDomainResponse(GravitinoValueDomainResponse response) {
    return convert(response, ValueDomainResponse.class);
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

  private <S, T> PageResponse<T> convertPage(GravitinoPageResult<S> source, Class<T> itemType) {
    PageResponse<T> page = new PageResponse<>();
    if (source == null) {
      page.setItems(List.of());
      page.setOffset(0);
      page.setLimit(0);
      page.setTotal(0L);
      return page;
    }
    page.setItems(convertList(source.getItems(), itemType));
    page.setOffset(source.getOffset());
    page.setLimit(source.getLimit());
    page.setTotal(source.getTotal());
    return page;
  }
}
