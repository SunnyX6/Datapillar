package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.InternalException;
import java.util.Map;

/**
 * LLM 内部错误异常
 * 描述 LLM 业务不可恢复系统错误语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class LlmInternalException extends InternalException {

    public LlmInternalException(String message, Object... args) {
        super(ErrorType.LLM_INTERNAL_ERROR, Map.of(), message, args);
    }

    public LlmInternalException(Throwable cause, String message, Object... args) {
        super(cause, ErrorType.LLM_INTERNAL_ERROR, Map.of(), message, args);
    }
}
