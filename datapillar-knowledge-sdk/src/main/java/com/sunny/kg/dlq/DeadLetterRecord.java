package com.sunny.kg.dlq;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 死信记录
 *
 * @author Sunny
 * @since 2025-12-11
 */
@Data
@Builder
public class DeadLetterRecord {

    /**
     * 记录 ID
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * 操作类型 (emitTable, emitLineage 等)
     */
    private String operation;

    /**
     * 原始数据（JSON 序列化）
     */
    private String payload;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 错误类型
     */
    private String errorType;

    /**
     * 重试次数
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * 创建时间
     */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * 最后重试时间
     */
    private Instant lastRetryAt;

}
