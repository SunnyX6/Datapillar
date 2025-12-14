package com.sunny.kg.internal.transport;

import com.sunny.kg.internal.mapper.CypherStatement;

import java.util.List;

/**
 * 传输层接口
 *
 * @author Sunny
 * @since 2025-12-10
 */
public interface Transport extends AutoCloseable {

    /**
     * 发送 Cypher 语句
     *
     * @param statement Cypher 语句
     */
    void send(CypherStatement statement);

    /**
     * 批量发送 Cypher 语句
     *
     * @param statements Cypher 语句列表
     */
    void send(List<CypherStatement> statements);

    /**
     * 刷新缓冲区
     */
    void flush();

    /**
     * 关闭传输层
     */
    @Override
    void close();

}
