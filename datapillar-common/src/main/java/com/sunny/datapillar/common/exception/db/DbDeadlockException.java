package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB Deadlock exception
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbDeadlockException extends DbStorageException {

  public DbDeadlockException(
      Throwable cause, Integer errorCode, String sqlState, String constraintName) {
    super(
        Code.SERVICE_UNAVAILABLE,
        ErrorType.DB_DEADLOCK,
        true,
        errorCode,
        sqlState,
        constraintName,
        cause,
        "Database deadlock");
  }
}
