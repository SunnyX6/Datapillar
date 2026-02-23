package com.sunny.datapillar.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
/**
 * 网关安全配置
 * 负责网关安全配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Configuration
@EnableConfigurationProperties({GatewaySecurityProperties.class, AuthenticationProperties.class})
public class GatewaySecurityConfig {
}
