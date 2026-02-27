package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import java.util.Map;

/**
 * LLM 未授权异常
 * 描述 LLM 场景身份缺失语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class LlmUnauthorizedException extends UnauthorizedException {

    public LlmUnauthorizedException(String message, Object... args) {
        super(ErrorType.LLM_UNAUTHORIZED, Map.of(), message, args);
    }

    public LlmUnauthorizedException(Throwable cause, String message, Object... args) {
        super(cause, ErrorType.LLM_UNAUTHORIZED, Map.of(), message, args);
    }
}
