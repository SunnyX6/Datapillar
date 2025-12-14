package com.sunny.kg.idempotent;

import java.time.Duration;

/**
 * 幂等性存储接口
 *
 * @author Sunny
 * @since 2025-12-11
 */
public interface IdempotentStore {

    /**
     * 检查并标记 key（原子操作）
     *
     * @param key 幂等键
     * @param ttl 过期时间
     * @return true 表示首次处理，false 表示重复
     */
    boolean checkAndMark(String key, Duration ttl);

    /**
     * 检查 key 是否已存在
     */
    boolean exists(String key);

    /**
     * 移除 key
     */
    void remove(String key);

    /**
     * 清空所有 key
     */
    void clear();

}
