package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ConflictException;
import java.util.Map;

/**
 * AI Model authorization conflict exception Description AI Model authorization unique constraint
 * conflict
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class AiModelGrantConflictException extends ConflictException {

  public AiModelGrantConflictException() {
    super(ErrorType.AI_MODEL_GRANT_CONFLICT, Map.of(), "AIModel authorization conflict");
  }

  public AiModelGrantConflictException(Throwable cause) {
    super(cause, ErrorType.AI_MODEL_GRANT_CONFLICT, Map.of(), "AIModel authorization conflict");
  }
}
