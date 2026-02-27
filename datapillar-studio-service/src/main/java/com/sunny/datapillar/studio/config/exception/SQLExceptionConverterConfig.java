package com.sunny.datapillar.studio.config.exception;

import com.sunny.datapillar.common.exception.db.SQLExceptionConverter;
import com.sunny.datapillar.common.exception.db.SQLExceptionConverterFactory;
import com.sunny.datapillar.common.exception.db.SQLExceptionUtils;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SQL 异常转换器配置
 *
 * @author Sunny
 * @date 2026-02-26
 */
@Configuration
public class SQLExceptionConverterConfig {

    @Bean
    public SQLExceptionConverter sqlExceptionConverter(DataSource dataSource) {
        SQLExceptionConverter converter = SQLExceptionConverterFactory.create(dataSource);
        SQLExceptionUtils.initialize(converter);
        return converter;
    }
}
