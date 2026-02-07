package com.sunny.datapillar.gateway.filter;

import com.sunny.datapillar.gateway.config.GatewaySecurityProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    private final GatewaySecurityProperties securityProperties;

    public SecurityHeadersFilter(GatewaySecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!securityProperties.getHeaders().isEnabled()) {
            return chain.filter(exchange);
        }

        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "no-referrer");

            if (securityProperties.isRequireHttps()) {
                String hstsValue = "max-age=" + securityProperties.getHeaders().getHstsMaxAgeSeconds();
                if (securityProperties.getHeaders().isIncludeSubDomains()) {
                    hstsValue = hstsValue + "; includeSubDomains";
                }
                headers.set("Strict-Transport-Security", hstsValue);
            }
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
