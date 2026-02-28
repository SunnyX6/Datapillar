package com.sunny.datapillar.openlineage.dao;

import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.model.OpenLineageUpdateResult;
import com.sunny.datapillar.openlineage.security.TenantContext;
import io.openlineage.client.OpenLineage;

/**
 * 图谱 DAO。
 */
public interface OpenLineageGraphDao {

    OpenLineageUpdateResult updateDatapillarModel(OpenLineage.RunEvent event,
                                                  OpenLineageEventEnvelope envelope,
                                                  TenantContext tenantContext);

    OpenLineageUpdateResult updateDatapillarModel(OpenLineage.DatasetEvent event,
                                                  OpenLineageEventEnvelope envelope,
                                                  TenantContext tenantContext);

    OpenLineageUpdateResult updateDatapillarModel(OpenLineage.JobEvent event,
                                                  OpenLineageEventEnvelope envelope,
                                                  TenantContext tenantContext);

    String fetchResourceContent(Long tenantId, String resourceType, String resourceId);

    void writeEmbedding(Long tenantId, String resourceId, String provider, double[] embedding);

    void writeSqlSummary(Long tenantId, String resourceId, String summary, String tags, String provider, double[] embedding);
}
