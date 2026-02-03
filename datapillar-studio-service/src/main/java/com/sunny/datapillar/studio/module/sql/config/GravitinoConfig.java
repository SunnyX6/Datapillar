package com.sunny.datapillar.studio.module.sql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Gravitino 配置
 *
 * @author sunny
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gravitino")
public class GravitinoConfig {

    /**
     * Gravitino 服务地址
     */
    private String uri;

    /**
     * Metalake 名称
     */
    private String metalake;
}
