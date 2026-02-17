package com.sunny.datapillar.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证代理配置属性
 * 承载认证代理路由与超时配置并完成参数绑定
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@ConfigurationProperties(prefix = "auth.proxy")
public class AuthProxyProperties {

    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 30000;
    private List<Route> routes = new ArrayList<>();

    @Data
    public static class Route {
        private String pathPrefix;
        private String serviceId;
        private String targetPrefix;
        private Boolean assertionEnabled = false;
    }
}
