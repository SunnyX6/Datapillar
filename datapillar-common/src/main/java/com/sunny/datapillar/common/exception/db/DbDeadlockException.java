package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB 死锁异常
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbDeadlockException extends DbStorageException {

    public DbDeadlockException(Throwable cause,
                               Integer errorCode,
                               String sqlState,
                               String constraintName) {
        super(
                Code.SERVICE_UNAVAILABLE,
                ErrorType.DB_DEADLOCK,
                true,
                errorCode,
                sqlState,
                constraintName,
                cause,
                "数据库死锁"
        );
    }
}
