package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.request.MetricCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.MetricUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.MetricVersionUpdateCommand;
import java.util.List;

public interface GravitinoMetricService {

  GravitinoPageResult<GravitinoMetricSummaryResponse> listMetrics(int offset, int limit);

  GravitinoMetricResponse createMetric(MetricCreateCommand command);

  GravitinoMetricResponse loadMetric(String code);

  GravitinoMetricResponse updateMetric(String code, MetricUpdateCommand command);

  boolean deleteMetric(String code);

  List<GravitinoMetricVersionSummaryResponse> listMetricVersions(String code);

  GravitinoMetricVersionResponse loadMetricVersion(String code, int version);

  GravitinoMetricVersionResponse updateMetricVersion(
      String code, int version, MetricVersionUpdateCommand command);

  GravitinoMetricVersionResponse switchMetricVersion(String code, int version);
}
