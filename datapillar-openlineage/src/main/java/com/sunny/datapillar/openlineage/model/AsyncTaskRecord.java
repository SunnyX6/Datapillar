package com.sunny.datapillar.openlineage.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 异步任务记录。
 */
@Data
public class AsyncTaskRecord {
    private Long id;
    private String taskType;
    private Long tenantId;
    private String tenantCode;
    private String resourceType;
    private String resourceId;
    private String contentHash;
    private String modelFingerprint;
    private String status;
    private Integer priority;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime nextRunAt;
    private String claimToken;
    private LocalDateTime claimUntil;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
