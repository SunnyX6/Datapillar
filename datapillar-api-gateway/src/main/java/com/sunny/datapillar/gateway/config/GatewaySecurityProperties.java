package com.sunny.datapillar.gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "security")
public class GatewaySecurityProperties {

    private boolean requireHttps = false;
    private List<String> allowedOrigins = new ArrayList<>();
    private Headers headers = new Headers();
    private Csrf csrf = new Csrf();

    @Data
    public static class Headers {
        private boolean enabled = true;
        private long hstsMaxAgeSeconds = 31536000;
        private boolean includeSubDomains = true;
    }

    @Data
    public static class Csrf {
        private boolean enabled = true;
        private String headerName = "X-CSRF-Token";
        private String cookieName = "csrf-token";
        private String refreshHeaderName = "X-Refresh-CSRF-Token";
        private String refreshCookieName = "refresh-csrf-token";
        private List<String> whitelist = new ArrayList<>();
    }
}
