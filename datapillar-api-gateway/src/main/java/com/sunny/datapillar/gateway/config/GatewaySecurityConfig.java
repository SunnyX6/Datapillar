package com.sunny.datapillar.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway security configuration Responsible for gateway security configuration, assembly
 * andBeaninitialization
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
@EnableConfigurationProperties({GatewaySecurityProperties.class, AuthenticationProperties.class})
public class GatewaySecurityConfig {}
