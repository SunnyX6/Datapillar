package com.sunny.datapillar.common.exception.db;

import com.sunny.datapillar.common.exception.db.dialect.H2ExceptionConverter;
import com.sunny.datapillar.common.exception.db.dialect.MySQLExceptionConverter;
import com.sunny.datapillar.common.exception.db.dialect.PostgreSQLExceptionConverter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JdbcExceptionConverterFactoryTest {

    @Test
    void create_shouldSelectDialectByJdbcUrl() {
        Assertions.assertInstanceOf(
                MySQLExceptionConverter.class,
                SQLExceptionConverterFactory.create("jdbc:mysql://localhost:3306/datapillar", null));
        Assertions.assertInstanceOf(
                PostgreSQLExceptionConverter.class,
                SQLExceptionConverterFactory.create("jdbc:postgresql://localhost:5432/datapillar", null));
        Assertions.assertInstanceOf(
                H2ExceptionConverter.class,
                SQLExceptionConverterFactory.create("jdbc:h2:mem:testdb", null));
    }

    @Test
    void create_shouldFallbackToMySqlWhenUnknown() {
        SQLExceptionConverter converter = SQLExceptionConverterFactory.create(
                "jdbc:sqlserver://localhost",
                "com.microsoft.sqlserver.jdbc.SQLServerDriver");

        Assertions.assertInstanceOf(MySQLExceptionConverter.class, converter);
    }

    @Test
    void create_shouldResolveDialectFromDataSourceMetadata() {
        DataSource dataSource = dataSource("jdbc:postgresql://localhost:5432/datapillar", "PostgreSQL JDBC Driver");

        SQLExceptionConverter converter = SQLExceptionConverterFactory.create(dataSource);

        Assertions.assertInstanceOf(PostgreSQLExceptionConverter.class, converter);
    }

    @Test
    void create_shouldFallbackWhenDataSourceUnavailable() {
        DataSource unavailableDataSource = (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        throw new SQLException("connection failed");
                    }
                    return defaultValue(method.getReturnType());
                });

        SQLExceptionConverter converter = SQLExceptionConverterFactory.create(unavailableDataSource);

        Assertions.assertInstanceOf(MySQLExceptionConverter.class, converter);
    }

    @Test
    void create_shouldFallbackWhenDataSourceNull() {
        SQLExceptionConverter converter = SQLExceptionConverterFactory.create((DataSource) null);

        Assertions.assertInstanceOf(MySQLExceptionConverter.class, converter);
    }

    private DataSource dataSource(String jdbcUrl, String driverName) {
        DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class[]{DatabaseMetaData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getURL" -> jdbcUrl;
                    case "getDriverName" -> driverName;
                    default -> defaultValue(method.getReturnType());
                });

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    if ("getMetaData".equals(method.getName())) {
                        return metadata;
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });

        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return connection;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        return null;
    }
}
