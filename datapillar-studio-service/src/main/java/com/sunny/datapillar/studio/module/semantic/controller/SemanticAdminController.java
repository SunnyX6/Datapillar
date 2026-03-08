package com.sunny.datapillar.studio.module.semantic.controller;

import com.sunny.datapillar.common.response.ApiResponse;
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
import com.sunny.datapillar.studio.dto.semantic.response.MetricVersionResponse;
import com.sunny.datapillar.studio.dto.semantic.response.ModifierResponse;
import com.sunny.datapillar.studio.dto.semantic.response.TagSummaryResponse;
import com.sunny.datapillar.studio.dto.semantic.response.UnitResponse;
import com.sunny.datapillar.studio.dto.semantic.response.ValueDomainResponse;
import com.sunny.datapillar.studio.dto.semantic.response.WordRootResponse;
import com.sunny.datapillar.studio.module.semantic.service.SemanticBizService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Semantic admin", description = "Semantic admin interface")
@RestController
@RequestMapping("/admin/semantic")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class SemanticAdminController {

  private final SemanticBizService semanticBizService;

  @Operation(summary = "Create word root")
  @PostMapping("/wordroots")
  public ApiResponse<WordRootResponse> createWordRoot(
      @Valid @RequestBody WordRootCreateRequest request) {
    return ApiResponse.ok(semanticBizService.createWordRoot(request));
  }

  @Operation(summary = "Update word root")
  @PutMapping("/wordroots/{code}")
  public ApiResponse<WordRootResponse> updateWordRoot(
      @PathVariable String code, @Valid @RequestBody WordRootUpdateRequest request) {
    return ApiResponse.ok(semanticBizService.updateWordRoot(code, request));
  }

  @Operation(summary = "Delete word root")
  @DeleteMapping("/wordroots/{code}")
  public ApiResponse<Boolean> deleteWordRoot(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.deleteWordRoot(code));
  }

  @Operation(summary = "Create metric")
  @PostMapping("/metrics")
  public ApiResponse<MetricResponse> createMetric(@Valid @RequestBody MetricCreateRequest request) {
    return ApiResponse.ok(semanticBizService.createMetric(request));
  }

  @Operation(summary = "Update metric")
  @PutMapping("/metrics/{code}")
  public ApiResponse<MetricResponse> updateMetric(
      @PathVariable String code, @Valid @RequestBody MetricUpdateRequest request) {
    return ApiResponse.ok(semanticBizService.updateMetric(code, request));
  }

  @Operation(summary = "Delete metric")
  @DeleteMapping("/metrics/{code}")
  public ApiResponse<Boolean> deleteMetric(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.deleteMetric(code));
  }

  @Operation(summary = "Update metric version")
  @PutMapping("/metrics/{code}/versions/{version}")
  public ApiResponse<MetricVersionResponse> updateMetricVersion(
      @PathVariable String code,
      @PathVariable int version,
      @Valid @RequestBody MetricVersionUpdateRequest request) {
    return ApiResponse.ok(semanticBizService.updateMetricVersion(code, version, request));
  }

  @Operation(summary = "Switch metric version")
  @PutMapping("/metrics/{code}/switch/versions/{version}")
  public ApiResponse<MetricVersionResponse> switchMetricVersion(
      @PathVariable String code, @PathVariable int version) {
    return ApiResponse.ok(semanticBizService.switchMetricVersion(code, version));
  }

  @Operation(summary = "Create unit")
  @PostMapping("/units")
  public ApiResponse<UnitResponse> createUnit(@Valid @RequestBody UnitCreateRequest request) {
    return ApiResponse.ok(semanticBizService.createUnit(request));
  }

  @Operation(summary = "Update unit")
  @PutMapping("/units/{code}")
  public ApiResponse<UnitResponse> updateUnit(
      @PathVariable String code, @Valid @RequestBody UnitUpdateRequest request) {
    return ApiResponse.ok(semanticBizService.updateUnit(code, request));
  }

  @Operation(summary = "Delete unit")
  @DeleteMapping("/units/{code}")
  public ApiResponse<Boolean> deleteUnit(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.deleteUnit(code));
  }

  @Operation(summary = "Create modifier")
  @PostMapping("/modifiers")
  public ApiResponse<ModifierResponse> createModifier(
      @Valid @RequestBody ModifierCreateRequest request) {
    return ApiResponse.ok(semanticBizService.createModifier(request));
  }

  @Operation(summary = "Update modifier")
  @PutMapping("/modifiers/{code}")
  public ApiResponse<ModifierResponse> updateModifier(
      @PathVariable String code, @Valid @RequestBody ModifierUpdateRequest request) {
    return ApiResponse.ok(semanticBizService.updateModifier(code, request));
  }

  @Operation(summary = "Delete modifier")
  @DeleteMapping("/modifiers/{code}")
  public ApiResponse<Boolean> deleteModifier(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.deleteModifier(code));
  }

  @Operation(summary = "Create value domain")
  @PostMapping("/value-domains")
  public ApiResponse<ValueDomainResponse> createValueDomain(
      @Valid @RequestBody ValueDomainCreateRequest request) {
    return ApiResponse.ok(semanticBizService.createValueDomain(request));
  }

  @Operation(summary = "Update value domain")
  @PutMapping("/value-domains/{code}")
  public ApiResponse<ValueDomainResponse> updateValueDomain(
      @PathVariable String code, @Valid @RequestBody ValueDomainUpdateRequest request) {
    return ApiResponse.ok(semanticBizService.updateValueDomain(code, request));
  }

  @Operation(summary = "Delete value domain")
  @DeleteMapping("/value-domains/{code}")
  public ApiResponse<Boolean> deleteValueDomain(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.deleteValueDomain(code));
  }

  @Operation(summary = "Alter object tags")
  @PostMapping("/objects/{objectType}/{fullName}/tags")
  public ApiResponse<List<TagSummaryResponse>> alterObjectTags(
      @PathVariable String objectType,
      @PathVariable String fullName,
      @Valid @RequestBody ObjectTagAlterRequest request) {
    return ApiResponse.ok(semanticBizService.alterObjectTags(objectType, fullName, request));
  }
}
