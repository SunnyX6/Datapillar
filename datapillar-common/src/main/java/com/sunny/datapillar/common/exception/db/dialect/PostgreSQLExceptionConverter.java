package com.sunny.datapillar.common.exception.db.dialect;

import com.sunny.datapillar.common.exception.db.ConstraintNameExtractor;
import com.sunny.datapillar.common.exception.db.DbCheckConstraintViolationException;
import com.sunny.datapillar.common.exception.db.DbConnectionFailedException;
import com.sunny.datapillar.common.exception.db.DbDataTooLongException;
import com.sunny.datapillar.common.exception.db.DbDeadlockException;
import com.sunny.datapillar.common.exception.db.DbForeignKeyViolationException;
import com.sunny.datapillar.common.exception.db.DbInternalException;
import com.sunny.datapillar.common.exception.db.DbLockTimeoutException;
import com.sunny.datapillar.common.exception.db.DbNotNullViolationException;
import com.sunny.datapillar.common.exception.db.DbStorageException;
import com.sunny.datapillar.common.exception.db.DbUniqueConstraintViolationException;
import com.sunny.datapillar.common.exception.db.SQLExceptionConverter;
import java.sql.SQLException;
import java.util.Locale;

/**
 * PostgreSQL SQL 异常转换器
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class PostgreSQLExceptionConverter implements SQLExceptionConverter {

    @Override
    public boolean supports(String jdbcUrl, String driverClassName) {
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        String driver = driverClassName == null ? "" : driverClassName.toLowerCase(Locale.ROOT);
        return url.contains(":postgresql:") || driver.contains("postgresql");
    }

    @Override
    public DbStorageException convert(SQLException sqlException) {
        Integer errorCode = sqlException.getErrorCode();
        String sqlState = sqlException.getSQLState();
        String constraintName = ConstraintNameExtractor.extract(sqlException).orElse(null);

        if (sqlState == null) {
            return new DbInternalException(sqlException, errorCode, null, constraintName);
        }

        switch (sqlState) {
            case "23505":
                return new DbUniqueConstraintViolationException(sqlException, errorCode, sqlState, constraintName);
            case "23503":
                return new DbForeignKeyViolationException(sqlException, errorCode, sqlState, constraintName);
            case "23502":
                return new DbNotNullViolationException(sqlException, errorCode, sqlState, constraintName);
            case "23514":
                return new DbCheckConstraintViolationException(sqlException, errorCode, sqlState, constraintName);
            case "22001":
                return new DbDataTooLongException(sqlException, errorCode, sqlState, constraintName);
            case "40P01":
                return new DbDeadlockException(sqlException, errorCode, sqlState, constraintName);
            case "55P03":
            case "57014":
                return new DbLockTimeoutException(sqlException, errorCode, sqlState, constraintName);
            default:
                break;
        }

        if (sqlState.startsWith("08") || "53300".equals(sqlState)) {
            return new DbConnectionFailedException(sqlException, errorCode, sqlState, constraintName);
        }

        return new DbInternalException(sqlException, errorCode, sqlState, constraintName);
    }
}
