package com.sunny.datapillar.studio.exception.user;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * There is an exception in the username Describe username unique constraint conflict semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class UsernameAlreadyExistsException extends AlreadyExistsException {

  private static final String TYPE = "USERNAME_ALREADY_EXISTS";

  public UsernameAlreadyExistsException() {
    super(TYPE, Map.of(), "Username already exists");
  }

  public UsernameAlreadyExistsException(Throwable cause) {
    super(cause, TYPE, Map.of(), "Username already exists");
  }
}
