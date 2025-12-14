package com.sunny.job.server.service;

import java.util.List;

/**
 * 任务日志 Service
 * <p>
 * 从文件系统读取任务执行日志
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public interface JobLogService {

    /**
     * 日志读取结果
     *
     * @param content    日志内容
     * @param offset     当前偏移量（字节）
     * @param hasMore    是否还有更多内容
     * @param fileExists 文件是否存在
     */
    record LogResult(String content, long offset, boolean hasMore, boolean fileExists) {}

    /**
     * 读取日志内容
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @param retryCount  重试次数
     * @param offset      起始偏移量（字节），0 表示从头开始
     * @param limit       最大读取字节数，0 表示读取全部
     * @return 日志读取结果
     */
    LogResult readLog(long namespaceId, long jobRunId, int retryCount, long offset, int limit);

    /**
     * 读取完整日志内容
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @param retryCount  重试次数
     * @return 日志内容，文件不存在返回 null
     */
    String readFullLog(long namespaceId, long jobRunId, int retryCount);

    /**
     * 获取任务的所有日志文件（含重试）
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @return 重试次数列表（按升序）
     */
    List<Integer> listRetryAttempts(long namespaceId, long jobRunId);

    /**
     * 检查日志文件是否存在
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @param retryCount  重试次数
     * @return 是否存在
     */
    boolean exists(long namespaceId, long jobRunId, int retryCount);
}
