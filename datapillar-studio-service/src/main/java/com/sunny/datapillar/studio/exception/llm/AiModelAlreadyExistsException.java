package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * AI The model already has an exception Description AI Model unique constraint conflict
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class AiModelAlreadyExistsException extends AlreadyExistsException {

  public AiModelAlreadyExistsException() {
    super(ErrorType.AI_MODEL_ALREADY_EXISTS, Map.of(), "AIModel already exists");
  }

  public AiModelAlreadyExistsException(Throwable cause) {
    super(cause, ErrorType.AI_MODEL_ALREADY_EXISTS, Map.of(), "AIModel already exists");
  }
}
