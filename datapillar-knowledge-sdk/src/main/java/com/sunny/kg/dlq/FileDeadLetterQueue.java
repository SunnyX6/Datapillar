package com.sunny.kg.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 基于文件的死信队列实现
 * <p>
 * 每条死信存储为一个独立的 JSON 文件，支持持久化和重放
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class FileDeadLetterQueue implements DeadLetterQueue {

    private static final Logger log = LoggerFactory.getLogger(FileDeadLetterQueue.class);
    private static final String FILE_SUFFIX = ".dlq.json";

    private final Path directory;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Path> recordFiles = new ConcurrentHashMap<>();
    private final AtomicInteger size = new AtomicInteger(0);

    public FileDeadLetterQueue(String directoryPath) {
        this.directory = Paths.get(directoryPath);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        try {
            Files.createDirectories(directory);
            loadExistingRecords();
            log.info("FileDeadLetterQueue 初始化完成, 目录: {}, 现有记录: {}", directory, size.get());
        } catch (IOException e) {
            throw new RuntimeException("无法创建 DLQ 目录: " + directoryPath, e);
        }
    }

    private void loadExistingRecords() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.toString().endsWith(FILE_SUFFIX))
                    .forEach(p -> {
                        String id = p.getFileName().toString().replace(FILE_SUFFIX, "");
                        recordFiles.put(id, p);
                        size.incrementAndGet();
                    });
        }
    }

    @Override
    public void write(DeadLetterRecord record) {
        Path file = directory.resolve(record.getId() + FILE_SUFFIX);
        try {
            String json = objectMapper.writeValueAsString(record);
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            recordFiles.put(record.getId(), file);
            size.incrementAndGet();
            log.debug("写入死信: {}", record.getId());
        } catch (IOException e) {
            log.error("写入死信失败: {}", record.getId(), e);
        }
    }

    @Override
    public List<DeadLetterRecord> readAll() {
        return read(Integer.MAX_VALUE);
    }

    @Override
    public List<DeadLetterRecord> read(int limit) {
        List<DeadLetterRecord> records = new ArrayList<>();
        int count = 0;

        for (Path file : recordFiles.values()) {
            if (count >= limit) {
                break;
            }
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                DeadLetterRecord record = objectMapper.readValue(json, DeadLetterRecord.class);
                records.add(record);
                count++;
            } catch (IOException e) {
                log.warn("读取死信文件失败: {}", file, e);
            }
        }

        return records;
    }

    @Override
    public void remove(String recordId) {
        Path file = recordFiles.remove(recordId);
        if (file != null) {
            try {
                Files.deleteIfExists(file);
                size.decrementAndGet();
                log.debug("删除死信: {}", recordId);
            } catch (IOException e) {
                log.warn("删除死信文件失败: {}", file, e);
            }
        }
    }

    @Override
    public void clear() {
        for (String id : new ArrayList<>(recordFiles.keySet())) {
            remove(id);
        }
        log.info("清空所有死信");
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public void close() {
        log.info("FileDeadLetterQueue 关闭, 剩余死信: {}", size.get());
    }

}
