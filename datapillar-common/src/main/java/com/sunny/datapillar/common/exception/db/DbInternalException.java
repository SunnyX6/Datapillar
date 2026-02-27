package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB 内部异常
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbInternalException extends DbStorageException {

    public DbInternalException(Throwable cause,
                               Integer errorCode,
                               String sqlState,
                               String constraintName) {
        super(
                Code.INTERNAL_ERROR,
                ErrorType.DB_INTERNAL_ERROR,
                false,
                errorCode,
                sqlState,
                constraintName,
                cause,
                "数据库内部错误"
        );
    }
}
