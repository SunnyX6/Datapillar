package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ConflictException;
import java.util.Map;

/**
 * AI 模型授权冲突异常
 * 描述 AI 模型授权唯一约束冲突
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class AiModelGrantConflictException extends ConflictException {

    public AiModelGrantConflictException() {
        super(ErrorType.AI_MODEL_GRANT_CONFLICT, Map.of(), "AI模型授权冲突");
    }

    public AiModelGrantConflictException(Throwable cause) {
        super(cause, ErrorType.AI_MODEL_GRANT_CONFLICT, Map.of(), "AI模型授权冲突");
    }
}
