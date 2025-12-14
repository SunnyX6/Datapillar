package com.sunny.kg.idempotent;

import com.sunny.kg.exception.KnowledgeException;

/**
 * 重复写入异常
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class DuplicateEmitException extends KnowledgeException {

    public DuplicateEmitException(String key) {
        super("DUPLICATE_EMIT", String.format("重复写入，幂等键: %s", key));
    }

}
