package com.sunny.job.server.service.impl;

import com.sunny.job.server.service.JobLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务日志 Service 实现
 * <p>
 * 从文件系统读取任务执行日志，支持增量读取（tail）
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobLogServiceImpl implements JobLogService {

    private static final Logger log = LoggerFactory.getLogger(JobLogServiceImpl.class);

    /**
     * 默认最大读取字节数（1MB）
     */
    private static final int DEFAULT_MAX_LIMIT = 1024 * 1024;

    /**
     * 日志文件名匹配模式：{jobRunId}_{retryCount}.log
     */
    private static final Pattern LOG_FILE_PATTERN = Pattern.compile("(\\d+)_(\\d+)\\.log");

    @Value("${datapillar.job.log.base-path}")
    private String basePath;

    @Override
    public LogResult readLog(long namespaceId, long jobRunId, int retryCount, long offset, int limit) {
        Path logFile = getLogFilePath(namespaceId, jobRunId, retryCount);

        if (!Files.exists(logFile)) {
            return new LogResult("", 0, false, false);
        }

        int readLimit = limit > 0 ? limit : DEFAULT_MAX_LIMIT;

        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long fileLength = raf.length();

            // offset 超出文件长度，返回空内容
            if (offset >= fileLength) {
                return new LogResult("", offset, false, true);
            }

            // 定位到指定偏移量
            raf.seek(offset);

            // 计算实际读取字节数
            int bytesToRead = (int) Math.min(readLimit, fileLength - offset);
            byte[] buffer = new byte[bytesToRead];
            int bytesRead = raf.read(buffer);

            if (bytesRead <= 0) {
                return new LogResult("", offset, false, true);
            }

            String content = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            long newOffset = offset + bytesRead;
            boolean hasMore = newOffset < fileLength;

            return new LogResult(content, newOffset, hasMore, true);

        } catch (IOException e) {
            log.error("读取日志文件失败: namespaceId={}, jobRunId={}, retryCount={}",
                    namespaceId, jobRunId, retryCount, e);
            return new LogResult("", offset, false, true);
        }
    }

    @Override
    public String readFullLog(long namespaceId, long jobRunId, int retryCount) {
        Path logFile = getLogFilePath(namespaceId, jobRunId, retryCount);

        if (!Files.exists(logFile)) {
            return null;
        }

        try {
            return Files.readString(logFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取完整日志失败: namespaceId={}, jobRunId={}, retryCount={}",
                    namespaceId, jobRunId, retryCount, e);
            return null;
        }
    }

    @Override
    public List<Integer> listRetryAttempts(long namespaceId, long jobRunId) {
        Path namespaceDir = Paths.get(basePath, String.valueOf(namespaceId));

        if (!Files.exists(namespaceDir) || !Files.isDirectory(namespaceDir)) {
            return Collections.emptyList();
        }

        List<Integer> attempts = new ArrayList<>();
        String prefix = jobRunId + "_";

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(namespaceDir, prefix + "*.log")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                Matcher matcher = LOG_FILE_PATTERN.matcher(fileName);
                if (matcher.matches()) {
                    long fileJobRunId = Long.parseLong(matcher.group(1));
                    if (fileJobRunId == jobRunId) {
                        int retryCount = Integer.parseInt(matcher.group(2));
                        attempts.add(retryCount);
                    }
                }
            }
        } catch (IOException e) {
            log.error("列出日志文件失败: namespaceId={}, jobRunId={}", namespaceId, jobRunId, e);
        }

        Collections.sort(attempts);
        return attempts;
    }

    @Override
    public boolean exists(long namespaceId, long jobRunId, int retryCount) {
        Path logFile = getLogFilePath(namespaceId, jobRunId, retryCount);
        return Files.exists(logFile);
    }

    /**
     * 获取日志文件路径
     * <p>
     * 路径格式：{basePath}/{namespaceId}/{jobRunId}_{retryCount}.log
     */
    private Path getLogFilePath(long namespaceId, long jobRunId, int retryCount) {
        String fileName = jobRunId + "_" + retryCount + ".log";
        return Paths.get(basePath, String.valueOf(namespaceId), fileName);
    }
}
