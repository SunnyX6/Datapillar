package com.sunny.kg.dlq;

import java.util.List;

/**
 * 死信队列接口
 * <p>
 * 用于存储写入失败的数据，支持后续重放
 *
 * @author Sunny
 * @since 2025-12-11
 */
public interface DeadLetterQueue extends AutoCloseable {

    /**
     * 写入死信
     *
     * @param record 死信记录
     */
    void write(DeadLetterRecord record);

    /**
     * 读取所有死信（用于重放）
     *
     * @return 死信列表
     */
    List<DeadLetterRecord> readAll();

    /**
     * 读取指定数量的死信
     *
     * @param limit 数量限制
     * @return 死信列表
     */
    List<DeadLetterRecord> read(int limit);

    /**
     * 删除已处理的死信
     *
     * @param recordId 记录 ID
     */
    void remove(String recordId);

    /**
     * 清空所有死信
     */
    void clear();

    /**
     * 获取死信数量
     */
    int size();

    /**
     * 关闭
     */
    @Override
    void close();

}
