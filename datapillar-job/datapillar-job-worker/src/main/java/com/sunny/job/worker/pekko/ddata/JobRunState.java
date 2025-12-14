package com.sunny.job.worker.pekko.ddata;

import com.sunny.job.core.enums.JobStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * job_run 状态（用于 CRDT 同步）
 * <p>
 * 存储 Dispatcher 内存中的 job_run 元数据，支持集群同步和故障恢复
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class JobRunState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long jobRunId;
    private final long workflowRunId;
    private final long jobId;
    private final long triggerTime;
    private final int status;
    private final int priority;
    private final List<Long> parentRunIds;
    private final long lastUpdateTime;

    public JobRunState(long jobRunId, long workflowRunId, long jobId,
                       long triggerTime, int status, int priority,
                       List<Long> parentRunIds, long lastUpdateTime) {
        this.jobRunId = jobRunId;
        this.workflowRunId = workflowRunId;
        this.jobId = jobId;
        this.triggerTime = triggerTime;
        this.status = status;
        this.priority = priority;
        this.parentRunIds = parentRunIds != null ? List.copyOf(parentRunIds) : List.of();
        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * 创建 WAITING 状态的 JobRunState
     */
    public static JobRunState createWaiting(long jobRunId, long workflowRunId, long jobId,
                                            long triggerTime, int priority, List<Long> parentRunIds) {
        return new JobRunState(
                jobRunId, workflowRunId, jobId,
                triggerTime, JobStatus.WAITING.getCode(), priority,
                parentRunIds, System.currentTimeMillis()
        );
    }

    /**
     * 创建状态更新后的新实例
     */
    public JobRunState withStatus(int newStatus) {
        return new JobRunState(
                jobRunId, workflowRunId, jobId,
                triggerTime, newStatus, priority,
                parentRunIds, System.currentTimeMillis()
        );
    }

    public long getJobRunId() {
        return jobRunId;
    }

    public long getWorkflowRunId() {
        return workflowRunId;
    }

    public long getJobId() {
        return jobId;
    }

    public long getTriggerTime() {
        return triggerTime;
    }

    public int getStatus() {
        return status;
    }

    public int getPriority() {
        return priority;
    }

    public List<Long> getParentRunIds() {
        return parentRunIds;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public boolean hasDependencies() {
        return parentRunIds != null && !parentRunIds.isEmpty();
    }

    public JobStatus getJobStatus() {
        return JobStatus.of(status);
    }

    public boolean isWaiting() {
        return status == JobStatus.WAITING.getCode();
    }

    public boolean isTerminal() {
        return JobStatus.of(status).isTerminal();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobRunState that = (JobRunState) o;
        return jobRunId == that.jobRunId &&
                workflowRunId == that.workflowRunId &&
                jobId == that.jobId &&
                triggerTime == that.triggerTime &&
                status == that.status &&
                priority == that.priority &&
                lastUpdateTime == that.lastUpdateTime &&
                Objects.equals(parentRunIds, that.parentRunIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobRunId, workflowRunId, jobId, triggerTime,
                status, priority, parentRunIds, lastUpdateTime);
    }

    @Override
    public String toString() {
        return "JobRunState{" +
                "jobRunId=" + jobRunId +
                ", workflowRunId=" + workflowRunId +
                ", jobId=" + jobId +
                ", triggerTime=" + triggerTime +
                ", status=" + status +
                ", priority=" + priority +
                ", parentRunIds=" + parentRunIds +
                ", lastUpdateTime=" + lastUpdateTime +
                '}';
    }
}
