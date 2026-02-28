package com.sunny.datapillar.openlineage.dao;

import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.model.OpenLineageUpdateResult;
import com.sunny.datapillar.openlineage.security.TenantContext;
import io.openlineage.client.OpenLineage;

/**
 * OpenLineage 组合 DAO。
 */
public interface OpenLineageDao {

    void createLineageEvent(OpenLineage.RunEvent event, OpenLineageEventEnvelope envelope, TenantContext tenantContext);

    void createDatasetEvent(OpenLineage.DatasetEvent event, OpenLineageEventEnvelope envelope, TenantContext tenantContext);

    void createJobEvent(OpenLineage.JobEvent event, OpenLineageEventEnvelope envelope, TenantContext tenantContext);

    OpenLineageUpdateResult updateDatapillarModel(OpenLineage.RunEvent event,
                                                  OpenLineageEventEnvelope envelope,
                                                  TenantContext tenantContext);

    OpenLineageUpdateResult updateDatapillarModel(OpenLineage.DatasetEvent event,
                                                  OpenLineageEventEnvelope envelope,
                                                  TenantContext tenantContext);

    OpenLineageUpdateResult updateDatapillarModel(OpenLineage.JobEvent event,
                                                  OpenLineageEventEnvelope envelope,
                                                  TenantContext tenantContext);
}
