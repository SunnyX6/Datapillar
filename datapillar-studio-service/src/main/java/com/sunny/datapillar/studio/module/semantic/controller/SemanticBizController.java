package com.sunny.datapillar.studio.module.semantic.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.config.openapi.OpenApiPaged;
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
import com.sunny.datapillar.studio.module.semantic.service.SemanticBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Semantic", description = "Semantic business interface")
@RestController
@RequestMapping("/biz/semantic")
@RequiredArgsConstructor
public class SemanticBizController {

  private final SemanticBizService semanticBizService;

  @OpenApiPaged
  @Operation(summary = "List word roots")
  @GetMapping("/wordroots")
  public ApiResponse<List<WordRootSummaryResponse>> listWordRoots(
      @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {
    PageResponse<WordRootSummaryResponse> page = semanticBizService.listWordRoots(offset, limit);
    return ApiResponse.page(page.getItems(), page.getLimit(), page.getOffset(), page.getTotal());
  }

  @Operation(summary = "Load word root")
  @GetMapping("/wordroots/{code}")
  public ApiResponse<WordRootResponse> loadWordRoot(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.loadWordRoot(code));
  }

  @OpenApiPaged
  @Operation(summary = "List metrics")
  @GetMapping("/metrics")
  public ApiResponse<List<MetricSummaryResponse>> listMetrics(
      @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {
    PageResponse<MetricSummaryResponse> page = semanticBizService.listMetrics(offset, limit);
    return ApiResponse.page(page.getItems(), page.getLimit(), page.getOffset(), page.getTotal());
  }

  @Operation(summary = "Load metric")
  @GetMapping("/metrics/{code}")
  public ApiResponse<MetricResponse> loadMetric(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.loadMetric(code));
  }

  @Operation(summary = "List metric versions")
  @GetMapping("/metrics/{code}/versions")
  public ApiResponse<List<MetricVersionSummaryResponse>> listMetricVersions(
      @PathVariable String code) {
    return ApiResponse.ok(semanticBizService.listMetricVersions(code));
  }

  @Operation(summary = "Load metric version")
  @GetMapping("/metrics/{code}/versions/{version}")
  public ApiResponse<MetricVersionResponse> loadMetricVersion(
      @PathVariable String code, @PathVariable int version) {
    return ApiResponse.ok(semanticBizService.loadMetricVersion(code, version));
  }

  @OpenApiPaged
  @Operation(summary = "List units")
  @GetMapping("/units")
  public ApiResponse<List<UnitSummaryResponse>> listUnits(
      @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {
    PageResponse<UnitSummaryResponse> page = semanticBizService.listUnits(offset, limit);
    return ApiResponse.page(page.getItems(), page.getLimit(), page.getOffset(), page.getTotal());
  }

  @Operation(summary = "Load unit")
  @GetMapping("/units/{code}")
  public ApiResponse<UnitResponse> loadUnit(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.loadUnit(code));
  }

  @OpenApiPaged
  @Operation(summary = "List modifiers")
  @GetMapping("/modifiers")
  public ApiResponse<List<ModifierSummaryResponse>> listModifiers(
      @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {
    PageResponse<ModifierSummaryResponse> page = semanticBizService.listModifiers(offset, limit);
    return ApiResponse.page(page.getItems(), page.getLimit(), page.getOffset(), page.getTotal());
  }

  @Operation(summary = "Load modifier")
  @GetMapping("/modifiers/{code}")
  public ApiResponse<ModifierResponse> loadModifier(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.loadModifier(code));
  }

  @OpenApiPaged
  @Operation(summary = "List value domains")
  @GetMapping("/value-domains")
  public ApiResponse<List<ValueDomainSummaryResponse>> listValueDomains(
      @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {
    PageResponse<ValueDomainSummaryResponse> page =
        semanticBizService.listValueDomains(offset, limit);
    return ApiResponse.page(page.getItems(), page.getLimit(), page.getOffset(), page.getTotal());
  }

  @Operation(summary = "Load value domain")
  @GetMapping("/value-domains/{code}")
  public ApiResponse<ValueDomainResponse> loadValueDomain(@PathVariable String code) {
    return ApiResponse.ok(semanticBizService.loadValueDomain(code));
  }

  @Operation(summary = "List object tags")
  @GetMapping("/objects/{objectType}/{fullName}/tags")
  public ApiResponse<List<TagSummaryResponse>> listObjectTags(
      @PathVariable String objectType, @PathVariable String fullName) {
    return ApiResponse.ok(semanticBizService.listObjectTags(objectType, fullName));
  }

  @Operation(summary = "Get owner")
  @GetMapping("/owners/{objectType}/{fullName}")
  public ApiResponse<OwnerResponse> getOwner(
      @PathVariable String objectType, @PathVariable String fullName) {
    return ApiResponse.ok(semanticBizService.getOwner(objectType, fullName));
  }
}
