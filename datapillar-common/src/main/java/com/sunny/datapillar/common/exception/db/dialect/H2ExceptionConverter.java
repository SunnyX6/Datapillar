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
 * H2 SQL 异常转换器
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class H2ExceptionConverter implements SQLExceptionConverter {

    private static final int ERROR_DUPLICATE_KEY = 23505;
    private static final int ERROR_DUPLICATE_KEY_MYSQL_MODE = 1062;
    private static final int ERROR_FOREIGN_KEY = 23503;
    private static final int ERROR_NOT_NULL = 23502;
    private static final int ERROR_CHECK = 23513;
    private static final int ERROR_TOO_LONG = 22001;
    private static final int ERROR_DEADLOCK = 40001;
    private static final int ERROR_LOCK_TIMEOUT = 50200;

    @Override
    public boolean supports(String jdbcUrl, String driverClassName) {
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        String driver = driverClassName == null ? "" : driverClassName.toLowerCase(Locale.ROOT);
        return url.contains(":h2:") || driver.contains("h2");
    }

    @Override
    public DbStorageException convert(SQLException sqlException) {
        Integer errorCode = sqlException.getErrorCode();
        String sqlState = sqlException.getSQLState();
        String constraintName = ConstraintNameExtractor.extract(sqlException).orElse(null);

        if (errorCode != null) {
            switch (errorCode) {
                case ERROR_DUPLICATE_KEY:
                case ERROR_DUPLICATE_KEY_MYSQL_MODE:
                    return new DbUniqueConstraintViolationException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_FOREIGN_KEY:
                    return new DbForeignKeyViolationException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_NOT_NULL:
                    return new DbNotNullViolationException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_CHECK:
                    return new DbCheckConstraintViolationException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_TOO_LONG:
                    return new DbDataTooLongException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_DEADLOCK:
                    return new DbDeadlockException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_LOCK_TIMEOUT:
                    return new DbLockTimeoutException(sqlException, errorCode, sqlState, constraintName);
                default:
                    break;
            }
        }

        if (sqlState != null) {
            switch (sqlState) {
                case "23505":
                    return new DbUniqueConstraintViolationException(sqlException, errorCode, sqlState, constraintName);
                case "23503":
                    return new DbForeignKeyViolationException(sqlException, errorCode, sqlState, constraintName);
                case "23502":
                    return new DbNotNullViolationException(sqlException, errorCode, sqlState, constraintName);
                case "23513":
                    return new DbCheckConstraintViolationException(sqlException, errorCode, sqlState, constraintName);
                case "22001":
                    return new DbDataTooLongException(sqlException, errorCode, sqlState, constraintName);
                case "40001":
                    return new DbDeadlockException(sqlException, errorCode, sqlState, constraintName);
                case "HYT00":
                case "HYT01":
                    return new DbLockTimeoutException(sqlException, errorCode, sqlState, constraintName);
                default:
                    break;
            }
            if (sqlState.startsWith("08")) {
                return new DbConnectionFailedException(sqlException, errorCode, sqlState, constraintName);
            }
        }

        return new DbInternalException(sqlException, errorCode, sqlState, constraintName);
    }
}
