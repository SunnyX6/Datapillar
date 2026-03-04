package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB Check for constraint violation exceptions
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbCheckConstraintViolationException extends DbStorageException {

  public DbCheckConstraintViolationException(
      Throwable cause, Integer errorCode, String sqlState, String constraintName) {
    super(
        Code.BAD_REQUEST,
        ErrorType.DB_CHECK_CONSTRAINT_VIOLATION,
        false,
        errorCode,
        sqlState,
        constraintName,
        cause,
        "Database check constraint violation");
  }
}
