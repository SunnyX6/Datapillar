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
import java.util.Set;

/**
 * MySQL SQL 异常转换器
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class MySQLExceptionConverter implements SQLExceptionConverter {

    private static final int ERROR_DUPLICATE_KEY = 1062;
    private static final int ERROR_FOREIGN_KEY_PARENT = 1451;
    private static final int ERROR_FOREIGN_KEY_CHILD = 1452;
    private static final int ERROR_NOT_NULL = 1048;
    private static final int ERROR_DATA_TOO_LONG = 1406;
    private static final int ERROR_CHECK_CONSTRAINT = 3819;
    private static final int ERROR_DEADLOCK = 1213;
    private static final int ERROR_LOCK_TIMEOUT = 1205;

    private static final Set<Integer> CONNECTION_ERROR_CODES = Set.of(
            1040, 1042, 1043, 1047, 1158, 1159, 1160, 1161, 2002, 2003, 2006, 2013
    );

    @Override
    public boolean supports(String jdbcUrl, String driverClassName) {
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        String driver = driverClassName == null ? "" : driverClassName.toLowerCase(Locale.ROOT);
        return url.contains(":mysql:") || driver.contains("mysql");
    }

    @Override
    public DbStorageException convert(SQLException sqlException) {
        Integer errorCode = sqlException.getErrorCode();
        String sqlState = sqlException.getSQLState();
        String constraintName = ConstraintNameExtractor.extract(sqlException).orElse(null);

        if (errorCode != null) {
            switch (errorCode) {
                case ERROR_DUPLICATE_KEY:
                    return new DbUniqueConstraintViolationException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_FOREIGN_KEY_PARENT:
                case ERROR_FOREIGN_KEY_CHILD:
                    return new DbForeignKeyViolationException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_NOT_NULL:
                    return new DbNotNullViolationException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_CHECK_CONSTRAINT:
                    return new DbCheckConstraintViolationException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_DATA_TOO_LONG:
                    return new DbDataTooLongException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_DEADLOCK:
                    return new DbDeadlockException(sqlException, errorCode, sqlState, constraintName);
                case ERROR_LOCK_TIMEOUT:
                    return new DbLockTimeoutException(sqlException, errorCode, sqlState, constraintName);
                default:
                    break;
            }
            if (CONNECTION_ERROR_CODES.contains(errorCode)) {
                return new DbConnectionFailedException(sqlException, errorCode, sqlState, constraintName);
            }
        }

        if (sqlState != null) {
            if (sqlState.startsWith("08")) {
                return new DbConnectionFailedException(sqlException, errorCode, sqlState, constraintName);
            }
            if ("23000".equals(sqlState) && containsDuplicateKeyword(sqlException.getMessage())) {
                return new DbUniqueConstraintViolationException(sqlException, errorCode, sqlState, constraintName);
            }
            if ("40001".equals(sqlState)) {
                return new DbDeadlockException(sqlException, errorCode, sqlState, constraintName);
            }
            if ("41000".equals(sqlState) || "HYT00".equals(sqlState)) {
                return new DbLockTimeoutException(sqlException, errorCode, sqlState, constraintName);
            }
        }

        return new DbInternalException(sqlException, errorCode, sqlState, constraintName);
    }

    private boolean containsDuplicateKeyword(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("duplicate") || normalized.contains("already exists");
    }
}
