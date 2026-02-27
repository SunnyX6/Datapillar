package com.sunny.datapillar.common.exception.db;

import java.sql.SQLException;

/**
 * SQL 异常转换器
 *
 * @author Sunny
 * @date 2026-02-26
 */
public interface SQLExceptionConverter {

    boolean supports(String jdbcUrl, String driverClassName);

    DbStorageException convert(SQLException sqlException);
}
