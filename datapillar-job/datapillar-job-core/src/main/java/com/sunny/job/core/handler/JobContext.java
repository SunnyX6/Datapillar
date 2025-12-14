package com.sunny.job.core.handler;

import com.sunny.job.core.enums.LogLevel;

/**
 * 任务执行上下文
 * <p>
 * 提供任务执行时的上下文信息，可作为 @DatapillarJob 方法的参数
 * <p>
 * SHARDING 模式下，提供 Split 分片信息：
 * - splitStart: 分片起点（包含）
 * - splitEnd: 分片终点（不包含）
 * - splitSize: 分片大小
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class JobContext {

    private static final ThreadLocal<JobContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 日志输出器（由 Worker 启动时设置）
     */
    private static volatile LogAppender LOG_APPENDER;

    private final long jobId;
    private final long instanceId;
    private final long namespaceId;
    private final String jobName;
    private final String jobType;
    private final String params;

    /**
     * 当前重试次数（0 表示首次执行）
     */
    private final int retryCount;

    // ============ Split 分片信息（SHARDING 模式）============

    /**
     * 分片起点（包含）
     */
    private final long splitStart;

    /**
     * 分片终点（不包含）
     */
    private final long splitEnd;

    /**
     * 分片大小
     */
    private final long splitSize;

    private int handleCode = HANDLE_CODE_SUCCESS;
    private String handleMsg;

    public static final int HANDLE_CODE_SUCCESS = 200;
    public static final int HANDLE_CODE_FAIL = 500;
    public static final int HANDLE_CODE_TIMEOUT = 502;

    public JobContext(long jobId, long instanceId, long namespaceId, String jobName,
                      String jobType, String params, int retryCount, long splitStart, long splitEnd) {
        this.jobId = jobId;
        this.instanceId = instanceId;
        this.namespaceId = namespaceId;
        this.jobName = jobName;
        this.jobType = jobType;
        this.params = params;
        this.retryCount = retryCount;
        this.splitStart = splitStart;
        this.splitEnd = splitEnd;
        this.splitSize = splitEnd - splitStart;
    }

    /**
     * 获取当前线程的任务上下文
     */
    public static JobContext get() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 设置当前线程的任务上下文
     */
    public static void set(JobContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 清除当前线程的任务上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 设置日志输出器（由 Worker 启动时调用）
     */
    public static void setLogAppender(LogAppender appender) {
        LOG_APPENDER = appender;
    }

    // ============ 日志记录方法 ============

    /**
     * 记录 INFO 日志
     */
    public void log(String content) {
        log(LogLevel.INFO, content);
    }

    /**
     * 记录 INFO 日志（格式化）
     */
    public void log(String format, Object... args) {
        log(LogLevel.INFO, String.format(format, args));
    }

    /**
     * 记录 WARN 日志
     */
    public void logWarn(String content) {
        log(LogLevel.WARN, content);
    }

    /**
     * 记录 WARN 日志（格式化）
     */
    public void logWarn(String format, Object... args) {
        log(LogLevel.WARN, String.format(format, args));
    }

    /**
     * 记录 ERROR 日志
     */
    public void logError(String content) {
        log(LogLevel.ERROR, content);
    }

    /**
     * 记录 ERROR 日志（格式化）
     */
    public void logError(String format, Object... args) {
        log(LogLevel.ERROR, String.format(format, args));
    }

    /**
     * 记录日志
     */
    private void log(LogLevel level, String content) {
        if (LOG_APPENDER != null) {
            LOG_APPENDER.append(namespaceId, instanceId, retryCount, level, content);
        }
    }

    // ============ Getter ============

    public long getJobId() {
        return jobId;
    }

    public long getInstanceId() {
        return instanceId;
    }

    public long getNamespaceId() {
        return namespaceId;
    }

    public String getJobName() {
        return jobName;
    }

    public String getJobType() {
        return jobType;
    }

    public String getParams() {
        return params;
    }

    /**
     * 获取当前重试次数
     * <p>
     * 0 表示首次执行，1 表示第一次重试，以此类推
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * 获取分片起点（包含）
     * <p>
     * SHARDING 模式下有效
     */
    public long getSplitStart() {
        return splitStart;
    }

    /**
     * 获取分片终点（不包含）
     * <p>
     * SHARDING 模式下有效
     */
    public long getSplitEnd() {
        return splitEnd;
    }

    /**
     * 获取分片大小
     * <p>
     * SHARDING 模式下有效
     */
    public long getSplitSize() {
        return splitSize;
    }

    /**
     * 是否为分片任务
     */
    public boolean isSharding() {
        return splitSize > 0;
    }

    public int getHandleCode() {
        return handleCode;
    }

    public String getHandleMsg() {
        return handleMsg;
    }

    // ============ 执行结果设置 ============

    /**
     * 设置执行成功
     */
    public void setSuccess() {
        this.handleCode = HANDLE_CODE_SUCCESS;
        this.handleMsg = null;
    }

    /**
     * 设置执行成功（带消息）
     */
    public void setSuccess(String msg) {
        this.handleCode = HANDLE_CODE_SUCCESS;
        this.handleMsg = msg;
    }

    /**
     * 设置执行失败
     */
    public void setFail(String msg) {
        this.handleCode = HANDLE_CODE_FAIL;
        this.handleMsg = msg;
    }

    /**
     * 设置执行超时
     */
    public void setTimeout(String msg) {
        this.handleCode = HANDLE_CODE_TIMEOUT;
        this.handleMsg = msg;
    }

    /**
     * 判断执行是否成功
     */
    public boolean isSuccess() {
        return handleCode == HANDLE_CODE_SUCCESS;
    }

    @Override
    public String toString() {
        return "JobContext{" +
                "jobId=" + jobId +
                ", instanceId=" + instanceId +
                ", namespaceId=" + namespaceId +
                ", jobName='" + jobName + '\'' +
                ", jobType=" + jobType +
                ", retryCount=" + retryCount +
                ", splitStart=" + splitStart +
                ", splitEnd=" + splitEnd +
                ", splitSize=" + splitSize +
                '}';
    }

    /**
     * 日志输出器接口
     * <p>
     * 由 Worker 实现，用于将日志写入文件
     */
    @FunctionalInterface
    public interface LogAppender {
        void append(long namespaceId, long jobRunId, int retryCount, LogLevel level, String content);
    }
}
