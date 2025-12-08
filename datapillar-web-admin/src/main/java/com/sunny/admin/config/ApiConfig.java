package com.sunny.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * API 配置类
 * 用于统一管理API前缀和版本信息
 * 
 * @author Sunny
 * @since 2024-01-01
 */
@Data
@Component
@ConfigurationProperties(prefix = "api")
public class ApiConfig {

    /**
     * API前缀，默认为 /api/v1
     */
    private String prefix = "/api";

    /**
     * API版本，默认为 v1
     */
    private String version = "v1";

    /**
     * 获取完整的API前缀路径
     * 
     * @param path 具体的API路径
     * @return 完整的API路径
     */
    public String getFullPath(String path) {
        if (path == null || path.isEmpty()) {
            return prefix;
        }

        // 确保path以/开头
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return prefix + path;
    }

    /**
     * 获取基础API前缀（不包含版本）
     * 
     * @return 基础API前缀
     */
    public String getBasePrefix() {
        return "/api";
    }

    /**
     * 获取版本化的前缀
     * 
     * @return 版本化的API前缀
     */
    public String getVersionedPrefix() {
        return getBasePrefix() + "/" + version;
    }
}