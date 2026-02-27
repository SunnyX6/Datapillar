package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.exception.db.dialect.H2ExceptionConverter;
import com.sunny.datapillar.common.exception.db.dialect.MySQLExceptionConverter;
import com.sunny.datapillar.common.exception.db.dialect.PostgreSQLExceptionConverter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

/**
 * SQL 异常转换器工厂
 *
 * @author Sunny
 * @date 2026-02-26
 */
public final class SQLExceptionConverterFactory {

    private static final List<SQLExceptionConverter> DIALECT_CONVERTERS = List.of(
            new MySQLExceptionConverter(),
            new PostgreSQLExceptionConverter(),
            new H2ExceptionConverter()
    );

    private static final SQLExceptionConverter FALLBACK_CONVERTER = new MySQLExceptionConverter();

    private SQLExceptionConverterFactory() {
    }

    public static SQLExceptionConverter create(String jdbcUrl, String driverClassName) {
        for (SQLExceptionConverter converter : DIALECT_CONVERTERS) {
            if (converter.supports(jdbcUrl, driverClassName)) {
                return converter;
            }
        }
        return FALLBACK_CONVERTER;
    }

    public static SQLExceptionConverter create(DataSource dataSource) {
        if (dataSource == null) {
            return FALLBACK_CONVERTER;
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String jdbcUrl = metadata == null ? null : metadata.getURL();
            String driverName = metadata == null ? null : metadata.getDriverName();
            return create(jdbcUrl, driverName);
        } catch (SQLException ex) {
            return FALLBACK_CONVERTER;
        }
    }
}
