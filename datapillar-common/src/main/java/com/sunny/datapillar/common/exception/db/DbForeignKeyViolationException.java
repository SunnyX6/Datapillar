package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;

/**
 * DB 外键约束冲突异常
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbForeignKeyViolationException extends DbStorageException {

    public DbForeignKeyViolationException(Throwable cause,
                                          Integer errorCode,
                                          String sqlState,
                                          String constraintName) {
        super(
                Code.CONFLICT,
                ErrorType.DB_FOREIGN_KEY_VIOLATION,
                false,
                errorCode,
                sqlState,
                constraintName,
                cause,
                "数据库外键约束冲突"
        );
    }
}
