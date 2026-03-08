package com.sunny.datapillar.studio.module.semantic.service.impl;

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
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoMetricService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoModifierService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoOwnerService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoTagService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUnitService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoValueDomainService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoWordRootService;
import com.sunny.datapillar.studio.module.semantic.mapper.SemanticDtoMapper;
import com.sunny.datapillar.studio.module.semantic.service.SemanticBizService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SemanticBizServiceImpl implements SemanticBizService {

  private final GravitinoWordRootService wordRootService;
  private final GravitinoMetricService metricService;
  private final GravitinoUnitService unitService;
  private final GravitinoModifierService modifierService;
  private final GravitinoValueDomainService valueDomainService;
  private final GravitinoTagService tagService;
  private final GravitinoOwnerService ownerService;
  private final SemanticDtoMapper semanticDtoMapper;

  @Override
  public PageResponse<WordRootSummaryResponse> listWordRoots(int offset, int limit) {
    return semanticDtoMapper.toWordRootPage(wordRootService.listWordRoots(offset, limit));
  }

  @Override
  public WordRootResponse createWordRoot(WordRootCreateRequest request) {
    return semanticDtoMapper.toWordRootResponse(
        wordRootService.createWordRoot(semanticDtoMapper.toWordRootCreateCommand(request)));
  }

  @Override
  public WordRootResponse loadWordRoot(String code) {
    return semanticDtoMapper.toWordRootResponse(wordRootService.loadWordRoot(code));
  }

  @Override
  public WordRootResponse updateWordRoot(String code, WordRootUpdateRequest request) {
    return semanticDtoMapper.toWordRootResponse(
        wordRootService.updateWordRoot(code, semanticDtoMapper.toWordRootUpdateCommand(request)));
  }

  @Override
  public boolean deleteWordRoot(String code) {
    return wordRootService.deleteWordRoot(code);
  }

  @Override
  public PageResponse<MetricSummaryResponse> listMetrics(int offset, int limit) {
    return semanticDtoMapper.toMetricPage(metricService.listMetrics(offset, limit));
  }

  @Override
  public MetricResponse createMetric(MetricCreateRequest request) {
    return semanticDtoMapper.toMetricResponse(
        metricService.createMetric(semanticDtoMapper.toMetricCreateCommand(request)));
  }

  @Override
  public MetricResponse loadMetric(String code) {
    return semanticDtoMapper.toMetricResponse(metricService.loadMetric(code));
  }

  @Override
  public MetricResponse updateMetric(String code, MetricUpdateRequest request) {
    return semanticDtoMapper.toMetricResponse(
        metricService.updateMetric(code, semanticDtoMapper.toMetricUpdateCommand(request)));
  }

  @Override
  public boolean deleteMetric(String code) {
    return metricService.deleteMetric(code);
  }

  @Override
  public List<MetricVersionSummaryResponse> listMetricVersions(String code) {
    return semanticDtoMapper.toMetricVersionSummaryResponses(
        metricService.listMetricVersions(code));
  }

  @Override
  public MetricVersionResponse loadMetricVersion(String code, int version) {
    return semanticDtoMapper.toMetricVersionResponse(
        metricService.loadMetricVersion(code, version));
  }

  @Override
  public MetricVersionResponse updateMetricVersion(
      String code, int version, MetricVersionUpdateRequest request) {
    return semanticDtoMapper.toMetricVersionResponse(
        metricService.updateMetricVersion(
            code, version, semanticDtoMapper.toMetricVersionUpdateCommand(request)));
  }

  @Override
  public MetricVersionResponse switchMetricVersion(String code, int version) {
    return semanticDtoMapper.toMetricVersionResponse(
        metricService.switchMetricVersion(code, version));
  }

  @Override
  public PageResponse<UnitSummaryResponse> listUnits(int offset, int limit) {
    return semanticDtoMapper.toUnitPage(unitService.listUnits(offset, limit));
  }

  @Override
  public UnitResponse createUnit(UnitCreateRequest request) {
    return semanticDtoMapper.toUnitResponse(
        unitService.createUnit(semanticDtoMapper.toUnitCreateCommand(request)));
  }

  @Override
  public UnitResponse loadUnit(String code) {
    return semanticDtoMapper.toUnitResponse(unitService.loadUnit(code));
  }

  @Override
  public UnitResponse updateUnit(String code, UnitUpdateRequest request) {
    return semanticDtoMapper.toUnitResponse(
        unitService.updateUnit(code, semanticDtoMapper.toUnitUpdateCommand(request)));
  }

  @Override
  public boolean deleteUnit(String code) {
    return unitService.deleteUnit(code);
  }

  @Override
  public PageResponse<ModifierSummaryResponse> listModifiers(int offset, int limit) {
    return semanticDtoMapper.toModifierPage(modifierService.listModifiers(offset, limit));
  }

  @Override
  public ModifierResponse createModifier(ModifierCreateRequest request) {
    return semanticDtoMapper.toModifierResponse(
        modifierService.createModifier(semanticDtoMapper.toModifierCreateCommand(request)));
  }

  @Override
  public ModifierResponse loadModifier(String code) {
    return semanticDtoMapper.toModifierResponse(modifierService.loadModifier(code));
  }

  @Override
  public ModifierResponse updateModifier(String code, ModifierUpdateRequest request) {
    return semanticDtoMapper.toModifierResponse(
        modifierService.updateModifier(code, semanticDtoMapper.toModifierUpdateCommand(request)));
  }

  @Override
  public boolean deleteModifier(String code) {
    return modifierService.deleteModifier(code);
  }

  @Override
  public PageResponse<ValueDomainSummaryResponse> listValueDomains(int offset, int limit) {
    return semanticDtoMapper.toValueDomainPage(valueDomainService.listValueDomains(offset, limit));
  }

  @Override
  public ValueDomainResponse createValueDomain(ValueDomainCreateRequest request) {
    return semanticDtoMapper.toValueDomainResponse(
        valueDomainService.createValueDomain(
            semanticDtoMapper.toValueDomainCreateCommand(request)));
  }

  @Override
  public ValueDomainResponse loadValueDomain(String code) {
    return semanticDtoMapper.toValueDomainResponse(valueDomainService.loadValueDomain(code));
  }

  @Override
  public ValueDomainResponse updateValueDomain(String code, ValueDomainUpdateRequest request) {
    return semanticDtoMapper.toValueDomainResponse(
        valueDomainService.updateValueDomain(
            code, semanticDtoMapper.toValueDomainUpdateCommand(request)));
  }

  @Override
  public boolean deleteValueDomain(String code) {
    return valueDomainService.deleteValueDomain(code);
  }

  @Override
  public List<TagSummaryResponse> listObjectTags(String objectType, String fullName) {
    return semanticDtoMapper.toTagSummaryResponses(
        tagService.listObjectTags(
            GravitinoDomainRoutingService.DOMAIN_SEMANTIC, objectType, fullName));
  }

  @Override
  public List<TagSummaryResponse> alterObjectTags(
      String objectType, String fullName, ObjectTagAlterRequest request) {
    return semanticDtoMapper.toTagSummaryResponses(
        tagService.alterObjectTags(
            GravitinoDomainRoutingService.DOMAIN_SEMANTIC,
            objectType,
            fullName,
            semanticDtoMapper.toObjectTagAlterCommand(request)));
  }

  @Override
  public OwnerResponse getOwner(String objectType, String fullName) {
    return semanticDtoMapper.toOwnerResponse(
        ownerService.getOwner(GravitinoDomainRoutingService.DOMAIN_SEMANTIC, objectType, fullName));
  }
}
