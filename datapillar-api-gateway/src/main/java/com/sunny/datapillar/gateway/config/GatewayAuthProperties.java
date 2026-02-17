package com.sunny.datapillar.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
/**
 * 网关认证配置属性
 * 承载网关认证配置项并完成参数绑定
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Data
@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {

    private List<String> whitelist = new ArrayList<>();
}
