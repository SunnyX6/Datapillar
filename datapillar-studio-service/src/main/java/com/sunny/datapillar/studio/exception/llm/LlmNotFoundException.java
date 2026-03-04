package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * LLM There is no exception in the resource Description LLM Business resource miss semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class LlmNotFoundException extends NotFoundException {

  public LlmNotFoundException(String message, Object... args) {
    super(ErrorType.LLM_RESOURCE_NOT_FOUND, Map.of(), message, args);
  }

  public LlmNotFoundException(Throwable cause, String message, Object... args) {
    super(cause, ErrorType.LLM_RESOURCE_NOT_FOUND, Map.of(), message, args);
  }
}
