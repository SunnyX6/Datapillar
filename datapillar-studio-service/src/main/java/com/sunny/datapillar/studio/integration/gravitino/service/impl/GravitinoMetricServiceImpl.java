package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoSemanticClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.request.MetricCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.MetricUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.MetricVersionUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoMetricService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoMetricServiceImpl implements GravitinoMetricService {

  private final GravitinoSemanticClient semanticClient;
  private final GravitinoClientFactory clientFactory;
  private final ObjectMapper objectMapper;

  @Override
  public GravitinoPageResult<GravitinoMetricSummaryResponse> listMetrics(int offset, int limit) {
    return semanticClient.listMetrics(clientFactory.requiredMetalake(), offset, limit);
  }

  @Override
  public GravitinoMetricResponse createMetric(MetricCreateCommand command) {
    return semanticClient.createMetric(
        clientFactory.requiredMetalake(), objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoMetricResponse loadMetric(String code) {
    return semanticClient.loadMetric(clientFactory.requiredMetalake(), code);
  }

  @Override
  public GravitinoMetricResponse updateMetric(String code, MetricUpdateCommand command) {
    return semanticClient.updateMetric(
        clientFactory.requiredMetalake(), code, objectMapper.valueToTree(command));
  }

  @Override
  public boolean deleteMetric(String code) {
    return semanticClient.deleteMetric(clientFactory.requiredMetalake(), code);
  }

  @Override
  public List<GravitinoMetricVersionSummaryResponse> listMetricVersions(String code) {
    return semanticClient.listMetricVersions(clientFactory.requiredMetalake(), code);
  }

  @Override
  public GravitinoMetricVersionResponse loadMetricVersion(String code, int version) {
    return semanticClient.loadMetricVersion(clientFactory.requiredMetalake(), code, version);
  }

  @Override
  public GravitinoMetricVersionResponse updateMetricVersion(
      String code, int version, MetricVersionUpdateCommand command) {
    return semanticClient.updateMetricVersion(
        clientFactory.requiredMetalake(), code, version, objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoMetricVersionResponse switchMetricVersion(String code, int version) {
    return semanticClient.switchMetricVersion(clientFactory.requiredMetalake(), code, version);
  }
}
