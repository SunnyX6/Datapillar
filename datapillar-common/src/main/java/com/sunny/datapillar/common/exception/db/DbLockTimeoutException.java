package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB 锁等待超时异常
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbLockTimeoutException extends DbStorageException {

    public DbLockTimeoutException(Throwable cause,
                                  Integer errorCode,
                                  String sqlState,
                                  String constraintName) {
        super(
                Code.SERVICE_UNAVAILABLE,
                ErrorType.DB_LOCK_TIMEOUT,
                true,
                errorCode,
                sqlState,
                constraintName,
                cause,
                "数据库锁等待超时"
        );
    }
}
