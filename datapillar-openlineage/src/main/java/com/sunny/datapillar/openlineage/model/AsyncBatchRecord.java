package com.sunny.datapillar.openlineage.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 异步批次记录。
 */
@Data
@Builder
public class AsyncBatchRecord {
    private Long id;
    private String batchNo;
    private String taskType;
    private Long tenantId;
    private String modelFingerprint;
    private String workerId;
    private Integer plannedSize;
    private Integer successCount;
    private Integer failedCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
}
