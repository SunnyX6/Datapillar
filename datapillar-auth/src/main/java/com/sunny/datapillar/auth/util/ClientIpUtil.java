package com.sunny.datapillar.auth.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.List;
/**
 * 客户端IP工具类
 * 提供客户端IP通用工具能力
 *
 * @author Sunny
 * @date 2026-01-01
 */

public final class ClientIpUtil {

    private ClientIpUtil() {
    }

    public static String getClientIp(HttpServletRequest request, List<String> trustedProxies) {
        if (request == null) {
            return "unknown";
        }

        String remoteIp = normalizeIp(request.getRemoteAddr());
        if (remoteIp == null) {
            return "unknown";
        }

        if (!isTrustedProxy(remoteIp, trustedProxies)) {
            return remoteIp;
        }

        String ip = resolveFromXForwardedFor(request.getHeader("X-Forwarded-For"), trustedProxies);
        if (ip != null) {
            return ip;
        }

        ip = normalizeIp(request.getHeader("X-Real-IP"));
        return ip == null ? remoteIp : ip;
    }

    public static String getClientIp(HttpServletRequest request) {
        return getClientIp(request, List.of());
    }

    private static String resolveFromXForwardedFor(String xff, List<String> trustedProxies) {
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
                if (!isTrustedProxy(candidate, trustedProxies)) {
                    return candidate;
                }
            }
        }
        return rightmostValid;
    }

    private static boolean isTrustedProxy(String ip, List<String> trustedProxies) {
        if (trustedProxies == null || trustedProxies.isEmpty()) {
            return false;
        }
        for (String rule : trustedProxies) {
            if (rule == null || rule.isBlank()) {
                continue;
            }
            if (matchesRule(ip, rule.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRule(String ip, String rule) {
        if (rule.contains("/")) {
            return matchesCidr(ip, rule);
        }
        String normalizedRule = normalizeIp(rule);
        return normalizedRule != null && normalizedRule.equals(ip);
    }

    private static boolean matchesCidr(String ip, String cidr) {
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

    private static String normalizeIp(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }

        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf("]"));
        } else if (value.chars().filter(ch -> ch == 58).count() == 1 && value.contains(".")) {
            value = value.substring(0, value.indexOf(":"));
        }

        int zoneIndex = value.indexOf("%");
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
