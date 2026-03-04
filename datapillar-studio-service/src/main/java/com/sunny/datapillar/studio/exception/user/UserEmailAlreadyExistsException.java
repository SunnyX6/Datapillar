package com.sunny.datapillar.studio.exception.user;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * There is an exception in the users mailbox Describe user mailbox unique constraint conflict
 * semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class UserEmailAlreadyExistsException extends AlreadyExistsException {

  private static final String TYPE = "USER_EMAIL_ALREADY_EXISTS";

  public UserEmailAlreadyExistsException() {
    super(TYPE, Map.of(), "Email already exists");
  }

  public UserEmailAlreadyExistsException(Throwable cause) {
    super(cause, TYPE, Map.of(), "Email already exists");
  }
}
