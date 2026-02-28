package com.sunny.datapillar.openlineage.service;

import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.security.TenantContext;
import io.openlineage.client.OpenLineage;

import java.util.concurrent.CompletableFuture;

/**
 * OpenLineage 服务接口。
 */
public interface OpenLineageService {

    CompletableFuture<Void> createAsync(OpenLineage.RunEvent event,
                                        OpenLineageEventEnvelope envelope,
                                        TenantContext tenantContext);

    CompletableFuture<Void> createAsync(OpenLineage.DatasetEvent event,
                                        OpenLineageEventEnvelope envelope,
                                        TenantContext tenantContext);

    CompletableFuture<Void> createAsync(OpenLineage.JobEvent event,
                                        OpenLineageEventEnvelope envelope,
                                        TenantContext tenantContext);
}
