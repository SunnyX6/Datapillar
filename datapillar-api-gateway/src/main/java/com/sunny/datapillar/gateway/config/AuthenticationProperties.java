package com.sunny.datapillar.gateway.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关认证配置属性
 * 承载网关认证规则与请求提取配置
 *
 * @author Sunny
 * @date 2026-02-19
 */
@Data
@ConfigurationProperties(prefix = "security.authentication")
public class AuthenticationProperties {

    private boolean enabled = true;
    private String authTokenCookieName = "auth-token";
    private String protocolVersion = "security.v1";
    private List<String> protectedPathPrefixes = new ArrayList<>(List.of(
            "/api/studio",
            "/api/ai",
            "/api/onemeta"
    ));
    private List<String> publicPathPrefixes = new ArrayList<>(List.of(
            "/api/login",
            "/api/auth",
            "/api/studio/setup",
            "/api/studio/actuator/health",
            "/api/studio/v3/api-docs",
            "/api/docs"
    ));
}
