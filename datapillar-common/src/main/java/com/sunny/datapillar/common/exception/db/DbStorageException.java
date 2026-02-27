package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import java.util.Locale;

/**
 * DB 存储异常基类
 * 描述数据库存储层统一异常元信息
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class DbStorageException extends DatapillarRuntimeException {

    private final Integer errorCode;
    private final String sqlState;
    private final String constraintName;

    protected DbStorageException(int code,
                                 String type,
                                 boolean retryable,
                                 Integer errorCode,
                                 String sqlState,
                                 String constraintName,
                                 Throwable cause,
                                 String message,
                                 Object... args) {
        super(cause, code, type, null, retryable, message, args);
        this.errorCode = errorCode;
        this.sqlState = normalizeSqlState(sqlState);
        this.constraintName = normalizeConstraintName(constraintName);
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public String getSqlState() {
        return sqlState;
    }

    public String getConstraintName() {
        return constraintName;
    }

    private static String normalizeConstraintName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSqlState(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
