package com.sunny.datapillar.studio.module.semantic.service;

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
import java.util.List;

public interface SemanticBizService {

  PageResponse<WordRootSummaryResponse> listWordRoots(int offset, int limit);

  WordRootResponse createWordRoot(WordRootCreateRequest request);

  WordRootResponse loadWordRoot(String code);

  WordRootResponse updateWordRoot(String code, WordRootUpdateRequest request);

  boolean deleteWordRoot(String code);

  PageResponse<MetricSummaryResponse> listMetrics(int offset, int limit);

  MetricResponse createMetric(MetricCreateRequest request);

  MetricResponse loadMetric(String code);

  MetricResponse updateMetric(String code, MetricUpdateRequest request);

  boolean deleteMetric(String code);

  List<MetricVersionSummaryResponse> listMetricVersions(String code);

  MetricVersionResponse loadMetricVersion(String code, int version);

  MetricVersionResponse updateMetricVersion(
      String code, int version, MetricVersionUpdateRequest request);

  MetricVersionResponse switchMetricVersion(String code, int version);

  PageResponse<UnitSummaryResponse> listUnits(int offset, int limit);

  UnitResponse createUnit(UnitCreateRequest request);

  UnitResponse loadUnit(String code);

  UnitResponse updateUnit(String code, UnitUpdateRequest request);

  boolean deleteUnit(String code);

  PageResponse<ModifierSummaryResponse> listModifiers(int offset, int limit);

  ModifierResponse createModifier(ModifierCreateRequest request);

  ModifierResponse loadModifier(String code);

  ModifierResponse updateModifier(String code, ModifierUpdateRequest request);

  boolean deleteModifier(String code);

  PageResponse<ValueDomainSummaryResponse> listValueDomains(int offset, int limit);

  ValueDomainResponse createValueDomain(ValueDomainCreateRequest request);

  ValueDomainResponse loadValueDomain(String code);

  ValueDomainResponse updateValueDomain(String code, ValueDomainUpdateRequest request);

  boolean deleteValueDomain(String code);

  List<TagSummaryResponse> listObjectTags(String objectType, String fullName);

  List<TagSummaryResponse> alterObjectTags(
      String objectType, String fullName, ObjectTagAlterRequest request);

  OwnerResponse getOwner(String objectType, String fullName);
}
