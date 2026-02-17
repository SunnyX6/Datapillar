package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.gateway.config.GatewaySecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    void resolve_shouldIgnoreXffWhenRemoteIsNotTrustedProxy() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setTrustedProxies(List.of("10.10.0.0/16"));
        ClientIpResolver resolver = new ClientIpResolver(properties);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/studio/projects")
                .header("X-Forwarded-For", "1.2.3.4")
                .remoteAddress(new InetSocketAddress("8.8.8.8", 443))
                .build();

        assertEquals("8.8.8.8", resolver.resolve(request));
    }

    @Test
    void resolve_shouldUseXffWhenRemoteIsTrustedProxy() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setTrustedProxies(List.of("10.10.0.0/16"));
        ClientIpResolver resolver = new ClientIpResolver(properties);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/studio/projects")
                .header("X-Forwarded-For", "1.2.3.4, 10.10.0.2")
                .remoteAddress(new InetSocketAddress("10.10.0.2", 443))
                .build();

        assertEquals("1.2.3.4", resolver.resolve(request));
    }

    @Test
    void resolve_shouldUseRightMostNonTrustedIpInXffChain() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setTrustedProxies(List.of("10.10.0.0/16"));
        ClientIpResolver resolver = new ClientIpResolver(properties);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/studio/projects")
                .header("X-Forwarded-For", "203.0.113.5, 198.51.100.9")
                .remoteAddress(new InetSocketAddress("10.10.0.2", 443))
                .build();

        assertEquals("198.51.100.9", resolver.resolve(request));
    }
}
