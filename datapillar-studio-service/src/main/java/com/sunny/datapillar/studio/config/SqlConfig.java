package com.sunny.datapillar.studio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * SQL配置
 * 负责SQL配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sql")
public class SqlConfig {

    /**
     * 是否启用 SQL 执行
     */
    private boolean enabled = true;

    /**
     * 最大返回行数
     */
    private int maxRows = 10000;

    /**
     * 执行超时时间（毫秒）
     */
    private long executionTimeout = 300000;
}
