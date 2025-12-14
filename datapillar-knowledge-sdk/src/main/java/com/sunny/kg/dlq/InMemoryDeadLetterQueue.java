package com.sunny.kg.dlq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的死信队列实现
 * <p>
 * 适用于测试或不需要持久化的场景
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class InMemoryDeadLetterQueue implements DeadLetterQueue {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDeadLetterQueue.class);

    private final ConcurrentHashMap<String, DeadLetterRecord> records = new ConcurrentHashMap<>();
    private final int maxSize;

    public InMemoryDeadLetterQueue() {
        this(10000);
    }

    public InMemoryDeadLetterQueue(int maxSize) {
        this.maxSize = maxSize;
        log.info("InMemoryDeadLetterQueue 初始化完成, maxSize: {}", maxSize);
    }

    @Override
    public void write(DeadLetterRecord record) {
        if (records.size() >= maxSize) {
            log.warn("死信队列已满 ({}), 丢弃最早的记录", maxSize);
            String oldestId = records.keySet().iterator().next();
            records.remove(oldestId);
        }
        records.put(record.getId(), record);
        log.debug("写入死信: {}", record.getId());
    }

    @Override
    public List<DeadLetterRecord> readAll() {
        return new ArrayList<>(records.values());
    }

    @Override
    public List<DeadLetterRecord> read(int limit) {
        return records.values().stream().limit(limit).toList();
    }

    @Override
    public void remove(String recordId) {
        records.remove(recordId);
        log.debug("删除死信: {}", recordId);
    }

    @Override
    public void clear() {
        records.clear();
        log.info("清空所有死信");
    }

    @Override
    public int size() {
        return records.size();
    }

    @Override
    public void close() {
        log.info("InMemoryDeadLetterQueue 关闭, 剩余死信: {}", records.size());
    }

}
