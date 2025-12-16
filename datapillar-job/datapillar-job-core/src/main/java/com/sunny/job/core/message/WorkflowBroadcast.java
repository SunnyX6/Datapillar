package com.sunny.job.core.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.sunny.job.core.enums.WorkflowOp;
import com.sunny.job.core.enums.WorkflowRunOp;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 工作流广播事件
 * <p>
 * Server 通过 CRDT 广播给所有 Worker
 * <p>
 * 支持两种级别的操作：
 * - WorkflowOp: 工作流级别（ONLINE, OFFLINE, MANUAL_TRIGGER）
 * - WorkflowRunOp: 运行实例级别（KILL, RERUN）
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowBroadcast implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一 ID（用于去重）
     */
    private String eventId;

    /**
     * 操作类型（存储枚举的 name()）
     */
    private String op;

    /**
     * 操作级别：WORKFLOW 或 WORKFLOW_RUN
     */
    private String opLevel;

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
            @JsonSubTypes.Type(value = OfflinePayload.class, name = "OFFLINE"),
            @JsonSubTypes.Type(value = KillPayload.class, name = "KILL"),
            @JsonSubTypes.Type(value = RerunPayload.class, name = "RERUN")
    })
    public sealed interface Payload extends Serializable
            permits TriggerPayload, OfflinePayload, KillPayload, RerunPayload {
    }

    /**
     * TRIGGER Payload（上线、手动触发共用）
     * <p>
     * Worker 使用 eventId + entityId 确定性计算 runId：
     * - workflowRunId = IdGenerator.deterministicId(eventId, workflowId)
     * - jobRunId = IdGenerator.deterministicId(eventId, jobId)
     */
    public record TriggerPayload(
            Long workflowId,
            Long namespaceId,
            List<Long> jobIds,
            List<DependencyInfo> dependencies
    ) implements Payload {
        private static final long serialVersionUID = 1L;
    }

    /**
     * OFFLINE Payload
     */
    public record OfflinePayload(
            Long workflowId
    ) implements Payload {
        private static final long serialVersionUID = 1L;
    }

    /**
     * KILL Payload
     */
    public record KillPayload(
            Long workflowRunId
    ) implements Payload {
        private static final long serialVersionUID = 1L;
    }

    /**
     * RERUN Payload
     */
    public record RerunPayload(
            Long workflowId,
            Long workflowRunId,
            Map<Long, Long> jobRunIdToJobIdMap
    ) implements Payload {
        private static final long serialVersionUID = 1L;
    }

    /**
     * 依赖关系信息（使用 jobId，Worker 自行计算 jobRunId）
     */
    public record DependencyInfo(
            long jobId,
            long parentJobId
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    // ============ 构造函数 ============

    /**
     * 默认构造函数（Jackson 反序列化需要）
     */
    public WorkflowBroadcast() {
    }

    private static final String LEVEL_WORKFLOW = "WORKFLOW";
    private static final String LEVEL_WORKFLOW_RUN = "WORKFLOW_RUN";

    private WorkflowBroadcast(String op, String opLevel, Payload payload) {
        this.eventId = UUID.randomUUID().toString();
        this.op = Objects.requireNonNull(op, "op 不能为空");
        this.opLevel = Objects.requireNonNull(opLevel, "opLevel 不能为空");
        this.timestamp = System.currentTimeMillis();
        this.payload = Objects.requireNonNull(payload, "payload 不能为空");
    }

    // ============ 静态工厂方法（WorkflowOp 级别） ============

    /**
     * 创建上线消息
     */
    public static WorkflowBroadcast online(TriggerPayload payload) {
        return new WorkflowBroadcast(WorkflowOp.ONLINE.name(), LEVEL_WORKFLOW, payload);
    }

    /**
     * 创建下线消息
     */
    public static WorkflowBroadcast offline(OfflinePayload payload) {
        return new WorkflowBroadcast(WorkflowOp.OFFLINE.name(), LEVEL_WORKFLOW, payload);
    }

    /**
     * 创建手动触发消息
     */
    public static WorkflowBroadcast manualTrigger(TriggerPayload payload) {
        return new WorkflowBroadcast(WorkflowOp.MANUAL_TRIGGER.name(), LEVEL_WORKFLOW, payload);
    }

    // ============ 静态工厂方法（WorkflowRunOp 级别） ============

    /**
     * 创建终止消息
     */
    public static WorkflowBroadcast kill(KillPayload payload) {
        return new WorkflowBroadcast(WorkflowRunOp.KILL.name(), LEVEL_WORKFLOW_RUN, payload);
    }

    /**
     * 创建重跑消息
     */
    public static WorkflowBroadcast rerun(RerunPayload payload) {
        return new WorkflowBroadcast(WorkflowRunOp.RERUN.name(), LEVEL_WORKFLOW_RUN, payload);
    }

    // ============ Getter/Setter ============

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getOpLevel() {
        return opLevel;
    }

    public void setOpLevel(String opLevel) {
        this.opLevel = opLevel;
    }

    /**
     * 判断是否是工作流级别操作
     */
    public boolean isWorkflowOp() {
        return LEVEL_WORKFLOW.equals(opLevel);
    }

    /**
     * 判断是否是运行实例级别操作
     */
    public boolean isWorkflowRunOp() {
        return LEVEL_WORKFLOW_RUN.equals(opLevel);
    }

    /**
     * 获取 WorkflowOp（如果是工作流级别操作）
     */
    public WorkflowOp getWorkflowOp() {
        if (!isWorkflowOp()) {
            return null;
        }
        try {
            return WorkflowOp.valueOf(op);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 获取 WorkflowRunOp（如果是运行实例级别操作）
     */
    public WorkflowRunOp getWorkflowRunOp() {
        if (!isWorkflowRunOp()) {
            return null;
        }
        try {
            return WorkflowRunOp.valueOf(op);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
     * 计算 workflow 应该由哪个 Bucket 负责
     */
    public int getWorkflowBucketId(int bucketCount) {
        Long workflowId = switch (payload) {
            case TriggerPayload p -> p.workflowId();
            case OfflinePayload p -> p.workflowId();
            case KillPayload p -> null;
            case RerunPayload p -> p.workflowId();
        };
        return workflowId != null ? (int) (workflowId % bucketCount) : -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowBroadcast that = (WorkflowBroadcast) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "WorkflowBroadcast{eventId='" + eventId + "', op=" + op + ", payload=" + payload + '}';
    }
}
