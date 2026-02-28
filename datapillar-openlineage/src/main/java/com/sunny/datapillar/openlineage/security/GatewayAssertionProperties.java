package com.sunny.datapillar.openlineage.security;

import com.sunny.datapillar.common.constant.HeaderConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关断言配置。
 */
@Data
@ConfigurationProperties(prefix = "security.gateway-assertion")
public class GatewayAssertionProperties {

    private boolean enabled = false;
    private String headerName = HeaderConstants.HEADER_GATEWAY_ASSERTION;
    private String issuer = "datapillar-auth";
    private String audience = "datapillar-openlineage";
    private String keyId = "auth-dev-2026-02";
    private String publicKeyPath;
    private String previousKeyId;
    private String previousPublicKeyPath;
    private long maxClockSkewSeconds = 5;
}
