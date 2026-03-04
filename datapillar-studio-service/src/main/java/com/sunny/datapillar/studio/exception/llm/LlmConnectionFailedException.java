package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ConnectionFailedException;
import java.util.Map;

/**
 * LLM Connection failure exception Description LLM External provider connection failure semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class LlmConnectionFailedException extends ConnectionFailedException {

  public LlmConnectionFailedException(String message, Object... args) {
    super(ErrorType.LLM_CONNECTION_FAILED, Map.of(), message, args);
  }

  public LlmConnectionFailedException(Throwable cause, String message, Object... args) {
    super(cause, ErrorType.LLM_CONNECTION_FAILED, Map.of(), message, args);
  }
}
