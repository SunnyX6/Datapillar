package io.datapillar.openlineage.transform.spark;

import io.datapillar.openlineage.transform.CompleteOnlyFilter;
import io.datapillar.openlineage.transform.EventFilter;
import io.openlineage.client.OpenLineage.DatasetEvent;
import io.openlineage.client.OpenLineage.JobEvent;
import io.openlineage.client.OpenLineage.RunEvent;
import io.openlineage.client.transports.EventTransformer;

import java.util.Map;

/**
 * Spark OpenLineage 事件转换器
 *
 * 功能：
 * 1. 过滤 START/RUNNING 事件，只保留 COMPLETE
 * 2. 过滤无输出的事件（纯 SELECT/SHOW）
 *
 * 配置示例：
 * spark.openlineage.transport.type=transform
 * spark.openlineage.transport.transformerClass=io.datapillar.openlineage.transform.spark.SparkEventTransformer
 * spark.openlineage.transport.transport.type=http
 * spark.openlineage.transport.transport.url=http://localhost:6003/api/v1/lineage
 */
public class SparkEventTransformer implements EventTransformer {

    private EventFilter eventFilter;
    private boolean filterEmptyOutputs;

    public SparkEventTransformer() {
        this.eventFilter = new CompleteOnlyFilter();
        this.filterEmptyOutputs = true;
    }

    @Override
    public void initialize(Map<String, String> properties) {
        if (properties != null) {
            this.filterEmptyOutputs = Boolean.parseBoolean(
                properties.getOrDefault("filterEmptyOutputs", "true")
            );
        }
    }

    @Override
    public RunEvent transform(RunEvent event) {
        // 1. 只保留 COMPLETE 事件
        if (!eventFilter.shouldEmit(event)) {
            return null;
        }

        // 2. 过滤无输出的事件（纯 SELECT/SHOW）
        if (filterEmptyOutputs && (event.getOutputs() == null || event.getOutputs().isEmpty())) {
            return null;
        }

        return event;
    }

    @Override
    public DatasetEvent transform(DatasetEvent event) {
        return event;
    }

    @Override
    public JobEvent transform(JobEvent event) {
        return event;
    }
}
