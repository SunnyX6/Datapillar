package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB Field length exceeds limit exception
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbDataTooLongException extends DbStorageException {

  public DbDataTooLongException(
      Throwable cause, Integer errorCode, String sqlState, String constraintName) {
    super(
        Code.BAD_REQUEST,
        ErrorType.DB_DATA_TOO_LONG,
        false,
        errorCode,
        sqlState,
        constraintName,
        cause,
        "Database field length exceeds limit");
  }
}
