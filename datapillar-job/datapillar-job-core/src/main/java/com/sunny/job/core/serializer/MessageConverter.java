package com.sunny.job.core.serializer;

import com.sunny.job.core.enums.BlockStrategy;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.enums.RouteStrategy;
import com.sunny.job.core.message.ExecutorMessage;
import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.core.message.SchedulerMessage;
import com.sunny.job.core.message.proto.*;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorRefResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息转换工具类
 * <p>
 * Java 消息对象 ↔ Protobuf 消息对象 双向转换
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public final class MessageConverter {

    private MessageConverter() {}

    // ==================== SchedulerMessage 转换 ====================

    public static SchedulerMessageProto toProto(SchedulerMessage msg) {
        SchedulerMessageProto.Builder builder = SchedulerMessageProto.newBuilder();

        switch (msg) {
            case SchedulerMessage.RegisterJob m -> builder.setRegisterJob(
                    RegisterJobProto.newBuilder()
                            .setJobRunId(m.jobRunId())
                            .setJobId(m.jobId())
                            .setTriggerTime(m.triggerTime())
                            .setPriority(m.priority())
                            .build()
            );
            case SchedulerMessage.JobCompleted m -> builder.setJobCompleted(
                    JobCompletedProto.newBuilder()
                            .setJobRunId(m.jobRunId())
                            .setWorkflowRunId(m.workflowRunId())
                            .setStatus(m.status())
                            .setMessage(m.message() != null ? m.message() : "")
                            .build()
            );
            case SchedulerMessage.TimerFired ignored -> builder.setTimerFired(
                    TimerFiredProto.newBuilder().build()
            );
            case SchedulerMessage.StartScan ignored -> builder.setStartScan(
                    StartScanProto.newBuilder().build()
            );
            case SchedulerMessage.WorkflowCompleted m -> builder.setWorkflowCompleted(
                    WorkflowCompletedProto.newBuilder()
                            .setWorkflowRunId(m.workflowRunId())
                            .setStatus(m.status())
                            .setMessage(m.message() != null ? m.message() : "")
                            .build()
            );
            case SchedulerMessage.BucketAcquired m -> builder.setBucketAcquired(
                    BucketAcquiredProto.newBuilder()
                            .setBucketId(m.bucketId())
                            .build()
            );
            case SchedulerMessage.BucketLost m -> builder.setBucketLost(
                    BucketLostProto.newBuilder()
                            .setBucketId(m.bucketId())
                            .build()
            );
            case SchedulerMessage.RenewBuckets ignored -> builder.setRenewBuckets(
                    RenewBucketsProto.newBuilder().build()
            );
            case SchedulerMessage.SplitCompleted m -> builder.setSplitCompleted(
                    SplitCompletedProto.newBuilder()
                            .setJobRunId(m.jobRunId())
                            .setSplitStart(m.splitStart())
                            .setSplitEnd(m.splitEnd())
                            .setStatus(m.status())
                            .setMessage(m.message() != null ? m.message() : "")
                            .setWorkerAddress(m.workerAddress() != null ? m.workerAddress() : "")
                            .build()
            );
            case SchedulerMessage.JobsLoaded m -> builder.setJobsLoaded(
                    JobsLoadedProto.newBuilder()
                            .addAllJobs(toProtoList(m.jobs()))
                            .setNewMaxId(m.newMaxId())
                            .setSource(m.source() != null ? m.source() : "")
                            .build()
            );
            case SchedulerMessage.JobsLoadFailed m -> builder.setJobsLoadFailed(
                    JobsLoadFailedProto.newBuilder()
                            .setReason(m.reason() != null ? m.reason() : "")
                            .setSource(m.source() != null ? m.source() : "")
                            .build()
            );
            case SchedulerMessage.ShardingReceiversUpdated ignored ->
                    throw new IllegalArgumentException("ShardingReceiversUpdated 包含 ActorRef Set，不支持跨节点序列化");
            case SchedulerMessage.RetrySplit ignored ->
                    throw new IllegalArgumentException("RetrySplit 是本地 Timer 消息，不支持跨节点序列化");
            case SchedulerMessage.GlobalMaxIdChanged ignored ->
                    throw new IllegalArgumentException("GlobalMaxIdChanged 是本地消息，不支持跨节点序列化");
            case SchedulerMessage.CancelWorkflow ignored ->
                    throw new IllegalArgumentException("CancelWorkflow 是本地消息，不支持跨节点序列化");
            case SchedulerMessage.CancelJob ignored ->
                    throw new IllegalArgumentException("CancelJob 是本地消息，不支持跨节点序列化");
            case SchedulerMessage.NewJobsCreated ignored ->
                    throw new IllegalArgumentException("NewJobsCreated 是本地消息，不支持跨节点序列化");
            case SchedulerMessage.RefreshJobInfo ignored ->
                    throw new IllegalArgumentException("RefreshJobInfo 是本地消息，不支持跨节点序列化");
        }

        return builder.build();
    }

    public static SchedulerMessage fromProto(SchedulerMessageProto proto) {
        return switch (proto.getMessageCase()) {
            case REGISTER_JOB -> {
                RegisterJobProto p = proto.getRegisterJob();
                yield new SchedulerMessage.RegisterJob(p.getJobRunId(), p.getJobId(), p.getTriggerTime(), p.getPriority());
            }
            case JOB_COMPLETED -> {
                JobCompletedProto p = proto.getJobCompleted();
                yield new SchedulerMessage.JobCompleted(p.getJobRunId(), p.getWorkflowRunId(), p.getStatus(), p.getMessage());
            }
            case TIMER_FIRED -> new SchedulerMessage.TimerFired();
            case START_SCAN -> new SchedulerMessage.StartScan();
            case WORKFLOW_COMPLETED -> {
                WorkflowCompletedProto p = proto.getWorkflowCompleted();
                yield new SchedulerMessage.WorkflowCompleted(p.getWorkflowRunId(), p.getStatus(), p.getMessage());
            }
            case BUCKET_ACQUIRED -> new SchedulerMessage.BucketAcquired(proto.getBucketAcquired().getBucketId());
            case BUCKET_LOST -> new SchedulerMessage.BucketLost(proto.getBucketLost().getBucketId());
            case RENEW_BUCKETS -> new SchedulerMessage.RenewBuckets();
            case SPLIT_COMPLETED -> {
                SplitCompletedProto p = proto.getSplitCompleted();
                yield new SchedulerMessage.SplitCompleted(p.getJobRunId(), p.getSplitStart(), p.getSplitEnd(),
                        p.getStatus(), p.getMessage(), p.getWorkerAddress());
            }
            case JOBS_LOADED -> {
                JobsLoadedProto p = proto.getJobsLoaded();
                yield new SchedulerMessage.JobsLoaded(fromProtoList(p.getJobsList()), p.getNewMaxId(), p.getSource());
            }
            case JOBS_LOAD_FAILED -> {
                JobsLoadFailedProto p = proto.getJobsLoadFailed();
                yield new SchedulerMessage.JobsLoadFailed(p.getReason(), p.getSource());
            }
            case MESSAGE_NOT_SET -> throw new IllegalArgumentException("SchedulerMessageProto 消息类型未设置");
        };
    }

    // ==================== ExecutorMessage 转换 ====================

    public static ExecutorMessageProto toProto(ExecutorMessage msg, ActorRefResolver resolver) {
        ExecutorMessageProto.Builder builder = ExecutorMessageProto.newBuilder();

        switch (msg) {
            case ExecutorMessage.ExecuteJob m -> {
                ExecuteJobProto.Builder jobBuilder = ExecuteJobProto.newBuilder()
                        .setJobRunId(m.jobRunId())
                        .setWorkflowRunId(m.workflowRunId())
                        .setJobId(m.jobId())
                        .setNamespaceId(m.namespaceId())
                        .setJobName(m.jobName() != null ? m.jobName() : "")
                        .setJobType(m.jobType() != null ? m.jobType() : "")
                        .setJobParams(m.jobParams() != null ? m.jobParams() : "")
                        .setRouteStrategy(m.routeStrategy() != null ? m.routeStrategy().getCode() : 0)
                        .setTimeoutSeconds(m.timeoutSeconds())
                        .setMaxRetryTimes(m.maxRetryTimes())
                        .setRetryInterval(m.retryInterval())
                        .setRetryCount(m.retryCount())
                        .setSplitStart(m.splitStart())
                        .setSplitEnd(m.splitEnd());

                // ActorRef 序列化为路径字符串
                if (m.schedulerRef() != null && resolver != null) {
                    jobBuilder.setSchedulerRefPath(resolver.toSerializationFormat(m.schedulerRef()));
                }

                builder.setExecuteJob(jobBuilder.build());
            }
            case ExecutorMessage.CancelJob m -> builder.setCancelJob(
                    CancelJobProto.newBuilder()
                            .setJobRunId(m.jobRunId())
                            .setReason(m.reason() != null ? m.reason() : "")
                            .build()
            );
            case ExecutorMessage.QueryStatus m -> builder.setQueryStatus(
                    QueryStatusProto.newBuilder()
                            .setJobRunId(m.jobRunId())
                            .build()
            );
            case ExecutorMessage.ExecutionTimeout m -> builder.setExecutionTimeout(
                    ExecutionTimeoutProto.newBuilder()
                            .setJobRunId(m.jobRunId())
                            .build()
            );
        }

        return builder.build();
    }

    public static ExecutorMessage fromProto(ExecutorMessageProto proto, ActorRefResolver resolver) {
        return switch (proto.getMessageCase()) {
            case EXECUTE_JOB -> {
                ExecuteJobProto p = proto.getExecuteJob();

                // ActorRef 反序列化
                ActorRef<SchedulerMessage> schedulerRef = null;
                if (!p.getSchedulerRefPath().isEmpty() && resolver != null) {
                    schedulerRef = resolver.resolveActorRef(p.getSchedulerRefPath());
                }

                yield new ExecutorMessage.ExecuteJob(
                        p.getJobRunId(),
                        p.getWorkflowRunId(),
                        p.getJobId(),
                        p.getNamespaceId(),
                        p.getJobName(),
                        p.getJobType().isEmpty() ? null : p.getJobType(),
                        p.getJobParams(),
                        p.getRouteStrategy() != 0 ? RouteStrategy.of(p.getRouteStrategy()) : null,
                        p.getTimeoutSeconds(),
                        p.getMaxRetryTimes(),
                        p.getRetryInterval(),
                        p.getRetryCount(),
                        p.getSplitStart(),
                        p.getSplitEnd(),
                        schedulerRef
                );
            }
            case CANCEL_JOB -> {
                CancelJobProto p = proto.getCancelJob();
                yield new ExecutorMessage.CancelJob(p.getJobRunId(), p.getReason());
            }
            case QUERY_STATUS -> new ExecutorMessage.QueryStatus(proto.getQueryStatus().getJobRunId());
            case EXECUTION_TIMEOUT -> new ExecutorMessage.ExecutionTimeout(proto.getExecutionTimeout().getJobRunId());
            case MESSAGE_NOT_SET -> throw new IllegalArgumentException("ExecutorMessageProto 消息类型未设置");
        };
    }

    // ==================== JobRunInfo 转换 ====================

    public static JobRunInfoProto toProto(JobRunInfo info) {
        JobRunInfoProto.Builder builder = JobRunInfoProto.newBuilder()
                .setJobRunId(info.getJobRunId())
                .setWorkflowRunId(info.getWorkflowRunId())
                .setJobId(info.getJobId())
                .setBucketId(info.getBucketId())
                .setNamespaceId(info.getNamespaceId())
                .setJobName(info.getJobName() != null ? info.getJobName() : "")
                .setJobType(info.getJobType() != null ? info.getJobType() : "")
                .setJobParams(info.getJobParams() != null ? info.getJobParams() : "")
                .setRouteStrategy(info.getRouteStrategy() != null ? info.getRouteStrategy().getCode() : 0)
                .setBlockStrategy(info.getBlockStrategy() != null ? info.getBlockStrategy().getCode() : 0)
                .setTimeoutSeconds(info.getTimeoutSeconds())
                .setMaxRetryTimes(info.getMaxRetryTimes())
                .setRetryInterval(info.getRetryInterval())
                .setPriority(info.getPriority())
                .setTriggerTime(info.getTriggerTime())
                .setStatus(info.getStatus() != null ? info.getStatus().getCode() : 0)
                .setRetryCount(info.getRetryCount());

        if (info.getParentJobRunIds() != null) {
            builder.addAllParentJobRunIds(info.getParentJobRunIds());
        }

        return builder.build();
    }

    public static JobRunInfo fromProto(JobRunInfoProto proto) {
        JobRunInfo info = new JobRunInfo();
        info.setJobRunId(proto.getJobRunId());
        info.setWorkflowRunId(proto.getWorkflowRunId());
        info.setJobId(proto.getJobId());
        info.setBucketId(proto.getBucketId());
        info.setNamespaceId(proto.getNamespaceId());
        info.setJobName(proto.getJobName());
        info.setJobType(proto.getJobType().isEmpty() ? null : proto.getJobType());
        info.setJobParams(proto.getJobParams());
        info.setRouteStrategy(proto.getRouteStrategy() != 0 ? RouteStrategy.of(proto.getRouteStrategy()) : null);
        info.setBlockStrategy(proto.getBlockStrategy() != 0 ? BlockStrategy.of(proto.getBlockStrategy()) : null);
        info.setTimeoutSeconds(proto.getTimeoutSeconds());
        info.setMaxRetryTimes(proto.getMaxRetryTimes());
        info.setRetryInterval(proto.getRetryInterval());
        info.setPriority(proto.getPriority());
        info.setTriggerTime(proto.getTriggerTime());
        info.setStatus(proto.getStatus() != 0 ? JobStatus.of(proto.getStatus()) : JobStatus.WAITING);
        info.setRetryCount(proto.getRetryCount());
        info.setParentJobRunIds(new ArrayList<>(proto.getParentJobRunIdsList()));
        return info;
    }

    // ==================== 批量转换 ====================

    private static List<JobRunInfoProto> toProtoList(List<JobRunInfo> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<JobRunInfoProto> result = new ArrayList<>(list.size());
        for (JobRunInfo info : list) {
            result.add(toProto(info));
        }
        return result;
    }

    private static List<JobRunInfo> fromProtoList(List<JobRunInfoProto> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<JobRunInfo> result = new ArrayList<>(list.size());
        for (JobRunInfoProto proto : list) {
            result.add(fromProto(proto));
        }
        return result;
    }
}
