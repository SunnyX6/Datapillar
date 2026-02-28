package com.sunny.datapillar.openlineage.dao.impl;

import com.sunny.datapillar.openlineage.dao.OpenLineageDao;
import com.sunny.datapillar.openlineage.dao.OpenLineageEventDao;
import com.sunny.datapillar.openlineage.dao.OpenLineageGraphDao;
import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.model.OpenLineageUpdateResult;
import com.sunny.datapillar.openlineage.security.TenantContext;
import io.openlineage.client.OpenLineage;
import org.springframework.stereotype.Repository;

/**
 * 组合 DAO 实现。
 */
@Repository
public class OpenLineageDaoImpl implements OpenLineageDao {

    private final OpenLineageEventDao eventDao;
    private final OpenLineageGraphDao graphDao;

    public OpenLineageDaoImpl(OpenLineageEventDao eventDao, OpenLineageGraphDao graphDao) {
        this.eventDao = eventDao;
        this.graphDao = graphDao;
    }

    @Override
    public void createLineageEvent(OpenLineage.RunEvent event,
                                   OpenLineageEventEnvelope envelope,
                                   TenantContext tenantContext) {
        eventDao.createLineageEvent(event, envelope, tenantContext);
    }

    @Override
    public void createDatasetEvent(OpenLineage.DatasetEvent event,
                                   OpenLineageEventEnvelope envelope,
                                   TenantContext tenantContext) {
        eventDao.createDatasetEvent(event, envelope, tenantContext);
    }

    @Override
    public void createJobEvent(OpenLineage.JobEvent event,
                               OpenLineageEventEnvelope envelope,
                               TenantContext tenantContext) {
        eventDao.createJobEvent(event, envelope, tenantContext);
    }

    @Override
    public OpenLineageUpdateResult updateDatapillarModel(OpenLineage.RunEvent event,
                                                         OpenLineageEventEnvelope envelope,
                                                         TenantContext tenantContext) {
        return graphDao.updateDatapillarModel(event, envelope, tenantContext);
    }

    @Override
    public OpenLineageUpdateResult updateDatapillarModel(OpenLineage.DatasetEvent event,
                                                         OpenLineageEventEnvelope envelope,
                                                         TenantContext tenantContext) {
        return graphDao.updateDatapillarModel(event, envelope, tenantContext);
    }

    @Override
    public OpenLineageUpdateResult updateDatapillarModel(OpenLineage.JobEvent event,
                                                         OpenLineageEventEnvelope envelope,
                                                         TenantContext tenantContext) {
        return graphDao.updateDatapillarModel(event, envelope, tenantContext);
    }
}
