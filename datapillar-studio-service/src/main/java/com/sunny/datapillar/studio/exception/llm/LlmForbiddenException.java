package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ForbiddenException;
import java.util.Map;

/**
 * LLM 访问拒绝异常
 * 描述 LLM 资源权限不足语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class LlmForbiddenException extends ForbiddenException {

    public LlmForbiddenException(String message, Object... args) {
        super(ErrorType.LLM_FORBIDDEN, Map.of(), message, args);
    }

    public LlmForbiddenException(Throwable cause, String message, Object... args) {
        super(cause, ErrorType.LLM_FORBIDDEN, Map.of(), message, args);
    }
}
