package com.sunny.datapillar.studio.security;

import com.sunny.datapillar.common.constant.HeaderConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关断言配置属性
 * 承载网关断言配置项并完成参数绑定
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@ConfigurationProperties(prefix = "security.gateway-assertion")
public class GatewayAssertionProperties {

    private boolean enabled = false;
    private String headerName = HeaderConstants.HEADER_GATEWAY_ASSERTION;
    private String issuer = "datapillar-gateway";
    private String audience = "datapillar-studio-service";
    private String keyId = "gw-default";
    private String publicKeyPath;
    private String previousKeyId;
    private String previousPublicKeyPath;
    private long maxClockSkewSeconds = 5;
}
