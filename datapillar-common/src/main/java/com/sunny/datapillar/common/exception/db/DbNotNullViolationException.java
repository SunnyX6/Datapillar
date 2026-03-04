package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB Non-null constraint violation exception
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbNotNullViolationException extends DbStorageException {

  public DbNotNullViolationException(
      Throwable cause, Integer errorCode, String sqlState, String constraintName) {
    super(
        Code.BAD_REQUEST,
        ErrorType.DB_NOT_NULL_VIOLATION,
        false,
        errorCode,
        sqlState,
        constraintName,
        cause,
        "Database non-null constraint violation");
  }
}
