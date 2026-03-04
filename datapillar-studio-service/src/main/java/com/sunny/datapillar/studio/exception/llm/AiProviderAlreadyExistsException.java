package com.sunny.datapillar.studio.exception.llm;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * AI The supplier already has an exception Description AI Supplier unique constraint conflict
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class AiProviderAlreadyExistsException extends AlreadyExistsException {

  public AiProviderAlreadyExistsException() {
    super(ErrorType.AI_PROVIDER_ALREADY_EXISTS, Map.of(), "AISupplier already exists");
  }

  public AiProviderAlreadyExistsException(Throwable cause) {
    super(cause, ErrorType.AI_PROVIDER_ALREADY_EXISTS, Map.of(), "AISupplier already exists");
  }
}
