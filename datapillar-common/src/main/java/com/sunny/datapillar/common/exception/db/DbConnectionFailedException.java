package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB 连接失败异常
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbConnectionFailedException extends DbStorageException {

    public DbConnectionFailedException(Throwable cause,
                                       Integer errorCode,
                                       String sqlState,
                                       String constraintName) {
        super(
                Code.SERVICE_UNAVAILABLE,
                ErrorType.DB_CONNECTION_FAILED,
                true,
                errorCode,
                sqlState,
                constraintName,
                cause,
                "数据库连接失败"
        );
    }
}
