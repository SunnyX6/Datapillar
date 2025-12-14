package com.sunny.job.worker.service;

import com.sunny.job.core.enums.LogLevel;
import com.sunny.job.core.handler.JobContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 任务日志服务
 * <p>
 * 提供任务执行过程中的日志记录功能
 * <p>
 * 设计要点：
 * 1. 使用 Logback SiftingAppender + MDC 实现按任务分文件
 * 2. 文件路径：{basePath}/{namespaceId}/{jobRunId}_{retryCount}.log
 * 3. 日志清理由 Logback 的 maxHistory 配置自动管理
 * 4. Server 端根据相同规则读取文件，实现实时日志查看
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobLogService {

    private static final Logger log = LoggerFactory.getLogger(JobLogService.class);

    /**
     * 任务日志专用 Logger（对应 logback-worker.xml 中的 JOB_LOGGER）
     */
    private static final Logger JOB_LOGGER = LoggerFactory.getLogger("JOB_LOGGER");

    /**
     * MDC key：日志文件路径（不含 basePath 和 .log 后缀）
     */
    private static final String MDC_KEY_JOB_LOG_FILE = "jobLogFile";

    @Value("${datapillar.job.worker.log.base-path:./logs/job}")
    private String basePath;

    @PostConstruct
    public void init() {
        // 设置 JobContext 的日志输出器
        JobContext.setLogAppender(this::appendLog);
        log.info("JobLogService 初始化完成，日志目录: {}", basePath);
    }

    /**
     * 追加日志
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @param retryCount  重试次数
     * @param level       日志级别
     * @param content     日志内容
     */
    public void appendLog(long namespaceId, long jobRunId, int retryCount, LogLevel level, String content) {
        // 设置 MDC，让 SiftingAppender 路由到正确的文件
        String logFileKey = buildLogFileKey(namespaceId, jobRunId, retryCount);
        MDC.put(MDC_KEY_JOB_LOG_FILE, logFileKey);
        try {
            switch (level) {
                case INFO -> JOB_LOGGER.info(content);
                case WARN -> JOB_LOGGER.warn(content);
                case ERROR -> JOB_LOGGER.error(content);
            }
        } finally {
            MDC.remove(MDC_KEY_JOB_LOG_FILE);
        }
    }

    /**
     * 记录 INFO 日志
     */
    public void info(long namespaceId, long jobRunId, int retryCount, String content) {
        appendLog(namespaceId, jobRunId, retryCount, LogLevel.INFO, content);
    }

    /**
     * 记录 WARN 日志
     */
    public void warn(long namespaceId, long jobRunId, int retryCount, String content) {
        appendLog(namespaceId, jobRunId, retryCount, LogLevel.WARN, content);
    }

    /**
     * 记录 ERROR 日志
     */
    public void error(long namespaceId, long jobRunId, int retryCount, String content) {
        appendLog(namespaceId, jobRunId, retryCount, LogLevel.ERROR, content);
    }

    /**
     * 构建日志文件 key（用于 MDC）
     * <p>
     * 格式：{namespaceId}/{jobRunId}_{retryCount}
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @param retryCount  重试次数
     * @return 日志文件 key
     */
    private String buildLogFileKey(long namespaceId, long jobRunId, int retryCount) {
        return namespaceId + "/" + jobRunId + "_" + retryCount;
    }

    /**
     * 获取日志文件路径
     * <p>
     * 路径格式：{basePath}/{namespaceId}/{jobRunId}_{retryCount}.log
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @param retryCount  重试次数
     * @return 日志文件路径
     */
    public Path getLogFilePath(long namespaceId, long jobRunId, int retryCount) {
        String fileName = jobRunId + "_" + retryCount + ".log";
        return Paths.get(basePath, String.valueOf(namespaceId), fileName);
    }

    /**
     * 获取日志基础路径
     */
    public String getBasePath() {
        return basePath;
    }
}
