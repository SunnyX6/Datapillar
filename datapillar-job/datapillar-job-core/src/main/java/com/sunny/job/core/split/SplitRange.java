package com.sunny.job.core.split;

import com.sunny.job.core.enums.SplitStatus;

import java.io.Serializable;

/**
 * 分片范围
 * <p>
 * 表示 SHARDING 策略下的一个 Split 范围
 * Worker 根据自己的能力拆分出一个范围，标记后执行
 *
 * @author Sunny
 */
public class SplitRange implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分片起点（包含）
     */
    private final long start;

    /**
     * 分片终点（不包含）
     */
    private final long end;

    /**
     * 分片状态
     */
    private volatile SplitStatus status;

    /**
     * 处理的 Worker 地址
     */
    private volatile String workerAddress;

    /**
     * 标记时间（毫秒）
     */
    private volatile long markTime;

    /**
     * 开始执行时间（毫秒）
     */
    private volatile long startTime;

    /**
     * 结束时间（毫秒）
     */
    private volatile long endTime;

    /**
     * 执行结果消息
     */
    private volatile String resultMessage;

    public SplitRange(long start, long end) {
        this.start = start;
        this.end = end;
        this.status = SplitStatus.PENDING;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    /**
     * 获取分片大小
     */
    public long getSize() {
        return end - start;
    }

    public SplitStatus getStatus() {
        return status;
    }

    public void setStatus(SplitStatus status) {
        this.status = status;
    }

    public String getWorkerAddress() {
        return workerAddress;
    }

    public void setWorkerAddress(String workerAddress) {
        this.workerAddress = workerAddress;
    }

    public long getMarkTime() {
        return markTime;
    }

    public void setMarkTime(long markTime) {
        this.markTime = markTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    /**
     * 标记为处理中
     */
    public void markProcessing(String workerAddress) {
        this.status = SplitStatus.PROCESSING;
        this.workerAddress = workerAddress;
        this.markTime = System.currentTimeMillis();
    }

    /**
     * 标记为已完成
     */
    public void markCompleted(String message) {
        this.status = SplitStatus.COMPLETED;
        this.endTime = System.currentTimeMillis();
        this.resultMessage = message;
    }

    /**
     * 标记为失败
     */
    public void markFailed(String message) {
        this.status = SplitStatus.FAILED;
        this.endTime = System.currentTimeMillis();
        this.resultMessage = message;
    }

    /**
     * 重置为待处理（用于故障恢复）
     */
    public void reset() {
        this.status = SplitStatus.PENDING;
        this.workerAddress = null;
        this.markTime = 0;
        this.startTime = 0;
        this.endTime = 0;
        this.resultMessage = null;
    }

    /**
     * 检查是否超时
     *
     * @param timeoutMs 超时时间（毫秒）
     */
    public boolean isTimeout(long timeoutMs) {
        if (status != SplitStatus.PROCESSING) {
            return false;
        }
        return System.currentTimeMillis() - markTime > timeoutMs;
    }

    @Override
    public String toString() {
        return "SplitRange{" +
                "start=" + start +
                ", end=" + end +
                ", status=" + status +
                ", worker=" + workerAddress +
                '}';
    }
}
