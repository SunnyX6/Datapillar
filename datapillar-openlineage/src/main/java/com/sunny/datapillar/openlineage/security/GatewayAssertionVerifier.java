package com.sunny.datapillar.openlineage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import com.sunny.datapillar.common.security.GatewayAssertionClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 网关断言校验器。
 */
@Component
public class GatewayAssertionVerifier {

    private final GatewayAssertionProperties properties;
    private final PublicKey publicKey;
    private final PublicKey previousPublicKey;
    private final ObjectMapper objectMapper;

    public GatewayAssertionVerifier(GatewayAssertionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (!properties.isEnabled()) {
            this.publicKey = null;
            this.previousPublicKey = null;
            return;
        }
        this.publicKey = EdDsaJwtSupport.loadPublicKey(properties.getPublicKeyPath());
        if (properties.getPreviousPublicKeyPath() == null || properties.getPreviousPublicKeyPath().isBlank()) {
            this.previousPublicKey = null;
        } else {
            this.previousPublicKey = EdDsaJwtSupport.loadPublicKey(properties.getPreviousPublicKeyPath());
        }
    }

    public GatewayAssertionContext verify(String token, String requestMethod, String requestPath) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("网关断言校验未启用");
        }
        if (token == null || token.isBlank()) {
            throw new SignatureException("缺少网关断言");
        }

        PublicKey verifyKey = resolveVerifyKey(token);
        Claims claims = Jwts.parser()
                .verifyWith(verifyKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!EdDsaJwtSupport.hasAudience(claims, properties.getAudience())) {
            throw new SignatureException("网关断言 audience 不匹配");
        }

        validateIssuedAt(claims);

        String assertedMethod = claims.get(GatewayAssertionClaims.METHOD, String.class);
        String assertedPath = claims.get(GatewayAssertionClaims.PATH, String.class);
        if (assertedMethod == null || !assertedMethod.equalsIgnoreCase(requestMethod)) {
            throw new SignatureException("网关断言 method 不匹配");
        }
        if (assertedPath == null || !assertedPath.equals(requestPath)) {
            throw new SignatureException("网关断言 path 不匹配");
        }

        Long userId = parseLong(claims.getSubject());
        Long tenantId = parseLong(claims.get(GatewayAssertionClaims.TENANT_ID));
        if (userId == null || tenantId == null) {
            throw new SignatureException("网关断言缺少主体信息");
        }

        List<String> roles = EdDsaJwtSupport.toStringList(claims.get(GatewayAssertionClaims.ROLES));

        return new GatewayAssertionContext(
                userId,
                tenantId,
                claims.get(GatewayAssertionClaims.TENANT_CODE, String.class),
                claims.get(GatewayAssertionClaims.TENANT_NAME, String.class),
                claims.get(GatewayAssertionClaims.USERNAME, String.class),
                claims.get(GatewayAssertionClaims.EMAIL, String.class),
                roles,
                Boolean.TRUE.equals(claims.get(GatewayAssertionClaims.IMPERSONATION, Boolean.class)),
                parseLong(claims.get(GatewayAssertionClaims.ACTOR_USER_ID)),
                parseLong(claims.get(GatewayAssertionClaims.ACTOR_TENANT_ID)),
                claims.getId());
    }

    private void validateIssuedAt(Claims claims) {
        if (claims.getIssuedAt() == null) {
            return;
        }
        long maxSkewSeconds = Math.max(0, properties.getMaxClockSkewSeconds());
        Instant allowedFutureTime = Instant.now().plusSeconds(maxSkewSeconds);
        if (claims.getIssuedAt().toInstant().isAfter(allowedFutureTime)) {
            throw new SignatureException("网关断言 iat 非法");
        }
    }

    private PublicKey resolveVerifyKey(String token) {
        String kid = extractKeyId(token);
        String primaryKid = normalize(properties.getKeyId());
        String previousKid = normalize(properties.getPreviousKeyId());

        if (primaryKid == null) {
            return publicKey;
        }
        if (kid == null) {
            throw new SignatureException("网关断言缺少 kid");
        }
        if (primaryKid.equals(kid)) {
            return publicKey;
        }
        if (previousKid != null && previousKid.equals(kid) && previousPublicKey != null) {
            return previousPublicKey;
        }
        throw new SignatureException("网关断言 kid 不匹配");
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String extractKeyId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new SignatureException("网关断言格式非法");
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            Map<?, ?> header = objectMapper.readValue(headerJson, Map.class);
            Object kid = header.get("kid");
            return normalize(kid == null ? null : kid.toString());
        } catch (SignatureException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SignatureException("网关断言头解析失败", ex);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
