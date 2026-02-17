package com.sunny.datapillar.studio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Gravitino配置
 * 负责Gravitino配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
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
