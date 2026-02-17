package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import com.sunny.datapillar.common.security.GatewayAssertionClaims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 认证断言签名器
 * 负责认证断言签名生成与验签支持
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class AuthAssertionSigner {

    private final AuthSecurityProperties.GatewayAssertion properties;
    private final PrivateKey privateKey;

    public AuthAssertionSigner(AuthSecurityProperties securityProperties) {
        this.properties = securityProperties.getGatewayAssertion();
        if (!properties.isEnabled()) {
            this.privateKey = null;
            return;
        }
        this.privateKey = EdDsaJwtSupport.loadPrivateKey(properties.getPrivateKeyPath());
    }

    public String sign(AssertionPayload payload) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("认证断言功能未启用");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(Math.max(1, properties.getTtlSeconds()));
        return Jwts.builder()
                .header().keyId(properties.getKeyId()).and()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(payload.userId()))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(GatewayAssertionClaims.AUDIENCE, properties.getAudience())
                .claim(GatewayAssertionClaims.TENANT_ID, payload.tenantId())
                .claim(GatewayAssertionClaims.USERNAME, payload.username())
                .claim(GatewayAssertionClaims.EMAIL, payload.email())
                .claim(GatewayAssertionClaims.ROLES, payload.roles())
                .claim(GatewayAssertionClaims.IMPERSONATION, payload.impersonation())
                .claim(GatewayAssertionClaims.ACTOR_USER_ID, payload.actorUserId())
                .claim(GatewayAssertionClaims.ACTOR_TENANT_ID, payload.actorTenantId())
                .claim(GatewayAssertionClaims.METHOD, payload.method())
                .claim(GatewayAssertionClaims.PATH, payload.path())
                .signWith(privateKey, Jwts.SIG.EdDSA)
                .compact();
    }

    public String headerName() {
        return properties.getHeaderName();
    }

    public record AssertionPayload(
            Long userId,
            Long tenantId,
            String username,
            String email,
            List<String> roles,
            boolean impersonation,
            Long actorUserId,
            Long actorTenantId,
            String method,
            String path) {

        public AssertionPayload {
            roles = roles == null ? Collections.emptyList() : roles;
        }
    }
}
