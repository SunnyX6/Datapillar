package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB Unique constraint violation exception
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbUniqueConstraintViolationException extends DbStorageException {

  public DbUniqueConstraintViolationException(
      Throwable cause, Integer errorCode, String sqlState, String constraintName) {
    super(
        Code.CONFLICT,
        ErrorType.DB_UNIQUE_CONSTRAINT_VIOLATION,
        false,
        errorCode,
        sqlState,
        constraintName,
        cause,
        "Database unique constraint violation");
  }
}
