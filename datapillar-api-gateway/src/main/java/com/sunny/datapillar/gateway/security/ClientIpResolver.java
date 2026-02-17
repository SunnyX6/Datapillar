package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.gateway.config.GatewaySecurityProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;

/**
 * 客户端IP解析器
 * 负责客户端IP解析流程与结果转换
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class ClientIpResolver {

    private final GatewaySecurityProperties securityProperties;

    public ClientIpResolver(GatewaySecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public String resolve(ServerHttpRequest request) {
        String remoteIp = extractRemoteIp(request);
        if (remoteIp == null) {
            return "unknown";
        }

        if (!isTrustedProxy(remoteIp)) {
            return remoteIp;
        }

        String fromXff = resolveFromXForwardedFor(request.getHeaders().getFirst("X-Forwarded-For"));
        if (fromXff != null) {
            return fromXff;
        }

        String realIp = normalizeIp(request.getHeaders().getFirst("X-Real-IP"));
        return realIp == null ? remoteIp : realIp;
    }

    private String extractRemoteIp(ServerHttpRequest request) {
        if (request == null || request.getRemoteAddress() == null || request.getRemoteAddress().getAddress() == null) {
            return null;
        }
        return normalizeIp(request.getRemoteAddress().getAddress().getHostAddress());
    }

    private String resolveFromXForwardedFor(String xff) {
        if (xff == null || xff.isBlank()) {
            return null;
        }
        String[] parts = xff.split(",");
        String rightmostValid = null;
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalizeIp(parts[i]);
            if (candidate != null) {
                if (rightmostValid == null) {
                    rightmostValid = candidate;
                }
                if (!isTrustedProxy(candidate)) {
                    return candidate;
                }
            }
        }
        return rightmostValid;
    }

    private boolean isTrustedProxy(String ip) {
        List<String> trusted = securityProperties.getTrustedProxies();
        if (trusted == null || trusted.isEmpty()) {
            return false;
        }
        for (String rule : trusted) {
            if (rule == null || rule.isBlank()) {
                continue;
            }
            if (matchesRule(ip, rule.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRule(String ip, String rule) {
        if (rule.contains("/")) {
            return matchesCidr(ip, rule);
        }
        String normalizedRule = normalizeIp(rule);
        return normalizedRule != null && normalizedRule.equals(ip);
    }

    private boolean matchesCidr(String ip, String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            return false;
        }
        String baseIp = normalizeIp(parts[0]);
        if (baseIp == null) {
            return false;
        }
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return false;
        }
        try {
            byte[] target = InetAddress.getByName(ip).getAddress();
            byte[] base = InetAddress.getByName(baseIp).getAddress();
            if (target.length != base.length) {
                return false;
            }
            int maxBits = target.length * 8;
            if (prefix < 0 || prefix > maxBits) {
                return false;
            }

            int fullBytes = prefix / 8;
            int remainder = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (target[i] != base[i]) {
                    return false;
                }
            }
            if (remainder == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainder)) & 0xFF;
            return (target[fullBytes] & mask) == (base[fullBytes] & mask);
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeIp(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }

        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf(']'));
        } else if (value.chars().filter(ch -> ch == ':').count() == 1 && value.contains(".")) {
            value = value.substring(0, value.indexOf(':'));
        }

        int zoneIndex = value.indexOf('%');
        if (zoneIndex > 0) {
            value = value.substring(0, zoneIndex);
        }

        if (!value.matches("^[0-9a-fA-F:.]+$")) {
            return null;
        }

        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception ex) {
            return null;
        }
    }
}
