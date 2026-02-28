package com.sunny.datapillar.openlineage.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * lineage_events 写入实体。
 */
@Data
@Builder
public class LineageEventRecord {
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private LocalDateTime eventTime;
    private String eventType;
    private String runUuid;
    private String jobName;
    private String jobNamespace;
    private String producer;
    private String internalEventType;
    private String eventJson;
}
