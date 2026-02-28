package com.sunny.datapillar.openlineage.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 异步任务执行明细。
 */
@Data
@Builder
public class AsyncTaskAttemptRecord {
    private Long id;
    private Long taskId;
    private Integer attemptNo;
    private String workerId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
    private String batchNo;
    private Integer inputSize;
    private Long latencyMs;
    private String errorType;
    private String errorMessage;
}
