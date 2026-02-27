package com.sunny.datapillar.common.exception.db;

import java.sql.SQLException;

/**
 * SQL 异常工具
 * 负责 RuntimeException 链路中的 SQL 异常统一收口
 *
 * @author Sunny
 * @date 2026-02-26
 */
public final class SQLExceptionUtils {

    private static final SQLExceptionConverter FALLBACK_CONVERTER =
            SQLExceptionConverterFactory.create((String) null, null);

    private static volatile SQLExceptionConverter converter = FALLBACK_CONVERTER;

    private SQLExceptionUtils() {
    }

    public static void initialize(SQLExceptionConverter configuredConverter) {
        converter = configuredConverter == null ? FALLBACK_CONVERTER : configuredConverter;
    }

    public static DbStorageException translate(Throwable throwable) {
        SQLException sqlException = findSQLException(throwable);
        if (sqlException == null) {
            return null;
        }
        return converter.convert(sqlException);
    }

    private static SQLException findSQLException(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlException) {
                return sqlException;
            }
            cursor = cursor.getCause();
        }
        return null;
    }
}
