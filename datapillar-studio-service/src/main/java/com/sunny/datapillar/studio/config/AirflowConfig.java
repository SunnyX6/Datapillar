package com.sunny.datapillar.studio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Airflow配置
 * 负责Airflow配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@Component
@ConfigurationProperties(prefix = "airflow")
public class AirflowConfig {

    /**
     * Airflow 服务地址
     */
    private String baseUrl;

    /**
     * 插件路径
     */
    private String pluginPath;

    /**
     * 认证用户名
     */
    private String username;

    /**
     * 认证密码
     */
    private String password;

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout;

    /**
     * 获取完整的插件 API URL
     */
    public String getPluginUrl() {
        return baseUrl + pluginPath;
    }

    /**
     * 获取认证 Token URL
     */
    public String getTokenUrl() {
        return baseUrl + "/auth/token";
    }
}
