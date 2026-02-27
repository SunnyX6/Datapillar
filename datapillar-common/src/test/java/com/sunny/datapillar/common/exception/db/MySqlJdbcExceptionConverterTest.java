package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.db.dialect.MySQLExceptionConverter;
import java.sql.SQLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MySqlJdbcExceptionConverterTest {

    private final MySQLExceptionConverter converter = new MySQLExceptionConverter();

    @Test
    void supports_shouldReturnTrueForMySqlUrlOrDriver() {
        Assertions.assertTrue(converter.supports("jdbc:mysql://localhost:3306/datapillar", null));
        Assertions.assertTrue(converter.supports(null, "com.mysql.cj.jdbc.Driver"));
        Assertions.assertFalse(converter.supports("jdbc:postgresql://localhost:5432/datapillar", "org.postgresql.Driver"));
    }

    @Test
    void convert_shouldResolveDuplicateKeyViolation() {
        SQLException sqlException = new SQLException(
                "Duplicate entry x@datapillar.ai for key uq_user_email",
                "23000",
                1062);

        DbStorageException ex = converter.convert(sqlException);

        Assertions.assertInstanceOf(DbUniqueConstraintViolationException.class, ex);
        Assertions.assertEquals(ErrorType.DB_UNIQUE_CONSTRAINT_VIOLATION, ex.getType());
        Assertions.assertEquals(1062, ex.getErrorCode());
        Assertions.assertEquals("23000", ex.getSqlState());
        Assertions.assertEquals("uq_user_email", ex.getConstraintName());
    }

    @Test
    void convert_shouldResolveForeignKeyViolation() {
        SQLException sqlException = new SQLException(
                "Cannot add or update a child row: a foreign key constraint fails",
                "23000",
                1452);

        DbStorageException ex = converter.convert(sqlException);

        Assertions.assertInstanceOf(DbForeignKeyViolationException.class, ex);
        Assertions.assertEquals(1452, ex.getErrorCode());
    }

    @Test
    void convert_shouldResolveDeadlockAndTimeout() {
        SQLException deadlock = new SQLException("Deadlock found", "40001", 1213);
        SQLException timeout = new SQLException("Lock wait timeout exceeded", "HYT00", 1205);

        DbStorageException deadlockEx = converter.convert(deadlock);
        DbStorageException timeoutEx = converter.convert(timeout);

        Assertions.assertInstanceOf(DbDeadlockException.class, deadlockEx);
        Assertions.assertTrue(deadlockEx.isRetryable());
        Assertions.assertInstanceOf(DbLockTimeoutException.class, timeoutEx);
        Assertions.assertTrue(timeoutEx.isRetryable());
    }

    @Test
    void convert_shouldResolveConnectionFailureBySqlState() {
        SQLException sqlException = new SQLException("Communications link failure", "08006", 0);

        DbStorageException ex = converter.convert(sqlException);

        Assertions.assertInstanceOf(DbConnectionFailedException.class, ex);
        Assertions.assertTrue(ex.isRetryable());
    }

    @Test
    void convert_shouldFallbackToInternalForUnknownError() {
        SQLException sqlException = new SQLException("random integrity error", "42000", 9999);

        DbStorageException ex = converter.convert(sqlException);

        Assertions.assertInstanceOf(DbInternalException.class, ex);
        Assertions.assertEquals(9999, ex.getErrorCode());
        Assertions.assertEquals("42000", ex.getSqlState());
    }
}
