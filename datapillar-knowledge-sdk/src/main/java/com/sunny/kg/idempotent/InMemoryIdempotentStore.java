package com.sunny.kg.idempotent;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的幂等性存储
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class InMemoryIdempotentStore implements IdempotentStore {

    private final ConcurrentHashMap<String, Instant> store = new ConcurrentHashMap<>();

    @Override
    public boolean checkAndMark(String key, Duration ttl) {
        cleanExpired();

        Instant expireAt = Instant.now().plus(ttl);
        Instant previous = store.putIfAbsent(key, expireAt);

        if (previous == null) {
            return true;
        }

        // 已存在，检查是否过期
        if (Instant.now().isAfter(previous)) {
            // 已过期，更新
            store.put(key, expireAt);
            return true;
        }

        return false;
    }

    @Override
    public boolean exists(String key) {
        Instant expireAt = store.get(key);
        if (expireAt == null) {
            return false;
        }
        if (Instant.now().isAfter(expireAt)) {
            store.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    @Override
    public void clear() {
        store.clear();
    }

    private void cleanExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }

}
