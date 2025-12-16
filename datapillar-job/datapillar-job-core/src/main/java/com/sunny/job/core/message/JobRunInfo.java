package com.sunny.job.core.message;

import com.sunny.job.core.enums.BlockStrategy;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.enums.RouteStrategy;

import java.io.Serializable;
import java.util.List;

/**
 * 任务运行信息
 * <p>
 * Dispatcher 内存中存储的任务元数据
 * <p>
 * 支持 MyBatis 映射，因此需要无参构造函数和 setter 方法
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class JobRunInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private long jobRunId;
    private long workflowRunId;
    private long workflowId;
    private long jobId;
    private int bucketId;
    private long namespaceId;
    private String jobName;
    private String jobType;
    private String jobParams;
    private RouteStrategy routeStrategy;
    private BlockStrategy blockStrategy;
    private int timeoutSeconds;
    private int maxRetryTimes;
    private int retryInterval;
    private int priority;
    private int triggerType;
    private long triggerTime;
    private JobStatus status;
    private int retryCount;

    /**
     * 父任务 ID 列表（依赖关系，由 DispatcherContext 加载后设置）
     */
    private List<Long> parentJobRunIds;

    /**
     * MyBatis 映射需要无参构造函数
     */
    public JobRunInfo() {
        this.status = JobStatus.WAITING;
        this.retryCount = 0;
    }

    // ============ Getter / Setter ============

    public long getJobRunId() {
        return jobRunId;
    }

    public void setJobRunId(long jobRunId) {
        this.jobRunId = jobRunId;
    }

    public long getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(long workflowRunId) {
        this.workflowRunId = workflowRunId;
    }

    public long getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(long workflowId) {
        this.workflowId = workflowId;
    }

    public long getJobId() {
        return jobId;
    }

    public void setJobId(long jobId) {
        this.jobId = jobId;
    }

    public int getBucketId() {
        return bucketId;
    }

    public void setBucketId(int bucketId) {
        this.bucketId = bucketId;
    }

    public long getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(long namespaceId) {
        this.namespaceId = namespaceId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getJobParams() {
        return jobParams;
    }

    public void setJobParams(String jobParams) {
        this.jobParams = jobParams;
    }

    public RouteStrategy getRouteStrategy() {
        return routeStrategy;
    }

    public void setRouteStrategy(RouteStrategy routeStrategy) {
        this.routeStrategy = routeStrategy;
    }

    public BlockStrategy getBlockStrategy() {
        return blockStrategy;
    }

    public void setBlockStrategy(BlockStrategy blockStrategy) {
        this.blockStrategy = blockStrategy;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    public void setMaxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(int triggerType) {
        this.triggerType = triggerType;
    }

    public long getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(long triggerTime) {
        this.triggerTime = triggerTime;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public List<Long> getParentJobRunIds() {
        return parentJobRunIds;
    }

    public void setParentJobRunIds(List<Long> parentJobRunIds) {
        this.parentJobRunIds = parentJobRunIds;
    }

    // ============ 业务方法 ============

    /**
     * 重试次数加一
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 是否可重试
     */
    public boolean canRetry() {
        return status != null && status.canRetry() && retryCount < maxRetryTimes;
    }

    /**
     * 是否有依赖
     */
    public boolean hasDependencies() {
        return parentJobRunIds != null && !parentJobRunIds.isEmpty();
    }

    @Override
    public String toString() {
        return "JobRunInfo{jobRunId=" + jobRunId + ", jobName='" + jobName + "', status=" + status + '}';
    }
}
