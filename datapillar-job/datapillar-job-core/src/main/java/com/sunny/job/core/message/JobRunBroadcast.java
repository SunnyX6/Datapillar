package com.sunny.job.core.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.sunny.job.core.enums.JobRunOp;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 任务级广播事件
 * <p>
 * Server 通过 CRDT 广播给 Worker，针对单个 job_run 的操作
 * <p>
 * 设计原则：
 * - 每个 Op 对应一个 Payload 类型，类型安全
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public class JobRunBroadcast implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一 ID（用于去重）
     */
    private String eventId;

    /**
     * 操作类型
     */
    private JobRunOp op;

    /**
     * 事件时间戳
     */
    private long timestamp;

    /**
     * 操作 Payload（多态，根据 Op 类型不同）
     */
    private Payload payload;

    // ============ Payload 定义 ============

    /**
     * Payload 基础接口（sealed，限定子类型）
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TriggerPayload.class, name = "TRIGGER"),
            @JsonSubTypes.Type(value = RetryPayload.class, name = "RETRY"),
            @JsonSubTypes.Type(value = KillPayload.class, name = "KILL"),
            @JsonSubTypes.Type(value = PassPayload.class, name = "PASS"),
            @JsonSubTypes.Type(value = MarkFailedPayload.class, name = "MARK_FAILED")
    })
    public sealed interface Payload extends Serializable
            permits TriggerPayload, RetryPayload, KillPayload, PassPayload, MarkFailedPayload {
    }

    /**
     * TRIGGER Payload
     */
    public record TriggerPayload(
            Long jobRunId,
            Long jobId,
            Long workflowRunId,
            Long namespaceId,
            Integer bucketId
    ) implements Payload {
        private static final long serialVersionUID = 1L;
    }

    /**
     * RETRY Payload
     */
    public record RetryPayload(
            Long jobRunId,
            Long jobId,
            Long workflowRunId,
            Long namespaceId,
            Integer bucketId
    ) implements Payload {
        private static final long serialVersionUID = 1L;
    }

    /**
     * KILL Payload
     */
    public record KillPayload(
            Long jobRunId
    ) implements Payload {
        private static final long serialVersionUID = 1L;
    }

    /**
     * PASS Payload
     */
    public record PassPayload(
            Long jobRunId
    ) implements Payload {
        private static final long serialVersionUID = 1L;
    }

    /**
     * MARK_FAILED Payload
     */
    public record MarkFailedPayload(
            Long jobRunId
    ) implements Payload {
        private static final long serialVersionUID = 1L;
    }

    // ============ 构造函数 ============

    /**
     * 默认构造函数（Jackson 反序列化需要）
     */
    public JobRunBroadcast() {
    }

    private JobRunBroadcast(JobRunOp op, Payload payload) {
        this.eventId = UUID.randomUUID().toString();
        this.op = Objects.requireNonNull(op, "op 不能为空");
        this.timestamp = System.currentTimeMillis();
        this.payload = Objects.requireNonNull(payload, "payload 不能为空");
    }

    // ============ 静态工厂方法 ============

    /**
     * 创建 TRIGGER 消息
     */
    public static JobRunBroadcast trigger(TriggerPayload payload) {
        return new JobRunBroadcast(JobRunOp.TRIGGER, payload);
    }

    /**
     * 创建 RETRY 消息
     */
    public static JobRunBroadcast retry(RetryPayload payload) {
        return new JobRunBroadcast(JobRunOp.RETRY, payload);
    }

    /**
     * 创建 KILL 消息
     */
    public static JobRunBroadcast kill(KillPayload payload) {
        return new JobRunBroadcast(JobRunOp.KILL, payload);
    }

    /**
     * 创建 PASS 消息
     */
    public static JobRunBroadcast pass(PassPayload payload) {
        return new JobRunBroadcast(JobRunOp.PASS, payload);
    }

    /**
     * 创建 MARK_FAILED 消息
     */
    public static JobRunBroadcast markFailed(MarkFailedPayload payload) {
        return new JobRunBroadcast(JobRunOp.MARK_FAILED, payload);
    }

    // ============ Getter/Setter ============

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public JobRunOp getOp() {
        return op;
    }

    public void setOp(JobRunOp op) {
        this.op = op;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Payload getPayload() {
        return payload;
    }

    public void setPayload(Payload payload) {
        this.payload = payload;
    }

    /**
     * 获取 Payload 并转换为指定类型
     */
    @SuppressWarnings("unchecked")
    public <T extends Payload> T getPayloadAs(Class<T> type) {
        if (type.isInstance(payload)) {
            return (T) payload;
        }
        throw new IllegalStateException("Payload 类型不匹配，期望 " + type + "，实际 " + payload.getClass());
    }

    /**
     * 获取 Bucket ID（用于路由）
     */
    public Integer getBucketId() {
        return switch (payload) {
            case TriggerPayload p -> p.bucketId();
            case RetryPayload p -> p.bucketId();
            default -> null;
        };
    }

    /**
     * 获取 jobRunId
     */
    public Long getJobRunId() {
        return switch (payload) {
            case TriggerPayload p -> p.jobRunId();
            case RetryPayload p -> p.jobRunId();
            case KillPayload p -> p.jobRunId();
            case PassPayload p -> p.jobRunId();
            case MarkFailedPayload p -> p.jobRunId();
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobRunBroadcast that = (JobRunBroadcast) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "JobRunBroadcast{eventId='" + eventId + "', op=" + op + ", payload=" + payload + '}';
    }
}
