package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * LLM Request parameter exception Description LLM Business request parameters are illegal and
 * semantic
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class LlmBadRequestException extends BadRequestException {

  public LlmBadRequestException(String message, Object... args) {
    super(ErrorType.LLM_REQUEST_INVALID, Map.of(), message, args);
  }

  public LlmBadRequestException(Throwable cause, String message, Object... args) {
    super(cause, ErrorType.LLM_REQUEST_INVALID, Map.of(), message, args);
  }
}
