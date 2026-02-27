package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * AI 供应商已存在异常
 * 描述 AI 供应商唯一约束冲突
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class AiProviderAlreadyExistsException extends AlreadyExistsException {

    public AiProviderAlreadyExistsException() {
        super(ErrorType.AI_PROVIDER_ALREADY_EXISTS, Map.of(), "AI供应商已存在");
    }

    public AiProviderAlreadyExistsException(Throwable cause) {
        super(cause, ErrorType.AI_PROVIDER_ALREADY_EXISTS, Map.of(), "AI供应商已存在");
    }
}
