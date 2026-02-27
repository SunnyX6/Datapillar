package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * AI 模型已存在异常
 * 描述 AI 模型唯一约束冲突
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class AiModelAlreadyExistsException extends AlreadyExistsException {

    public AiModelAlreadyExistsException() {
        super(ErrorType.AI_MODEL_ALREADY_EXISTS, Map.of(), "AI模型已存在");
    }

    public AiModelAlreadyExistsException(Throwable cause) {
        super(cause, ErrorType.AI_MODEL_ALREADY_EXISTS, Map.of(), "AI模型已存在");
    }
}
