package com.sunny.datapillar.common.utils;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * JWT 通用工具（签发/解析/校验）
 */
public class JwtUtil {

    private final SecretKey key;
    private final String issuer;

    public JwtUtil(String secret, String issuer) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT 密钥长度至少 32 位");
        }
        this.key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        this.issuer = issuer;
    }

    public String sign(Map<String, Object> claims, String subject, Date issuedAt, Date expiration) {
        if (claims == null || subject == null || issuedAt == null || expiration == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return parseToken(token, false);
    }

    public Claims parseTokenWithIssuer(String token) {
        return parseToken(token, true);
    }

    public boolean isValid(String token) {
        return isValid(token, false);
    }

    public boolean isValidWithIssuer(String token) {
        return isValid(token, true);
    }

    public Long getUserId(Claims claims) {
        if (claims == null) {
            return null;
        }
        return parseLong(claims.getSubject());
    }

    public Long getTenantId(Claims claims) {
        if (claims == null) {
            return null;
        }
        return parseLong(claims.get("tenantId"));
    }

    public String getUsername(Claims claims) {
        if (claims == null) {
            return null;
        }
        return claims.get("username", String.class);
    }

    public String getEmail(Claims claims) {
        if (claims == null) {
            return null;
        }
        return claims.get("email", String.class);
    }

    public String getTokenType(Claims claims) {
        if (claims == null) {
            return null;
        }
        return claims.get("tokenType", String.class);
    }

    public Boolean getRememberMe(Claims claims) {
        if (claims == null) {
            return null;
        }
        return claims.get("rememberMe", Boolean.class);
    }

    public Long getActorUserId(Claims claims) {
        if (claims == null) {
            return null;
        }
        return parseLong(claims.get("actorUserId"));
    }

    public Long getActorTenantId(Claims claims) {
        if (claims == null) {
            return null;
        }
        return parseLong(claims.get("actorTenantId"));
    }

    public boolean isImpersonation(Claims claims) {
        if (claims == null) {
            return false;
        }
        return Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
    }

    public String extractTokenSignature(String token) {
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "JWT 不能为空");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "JWT 格式非法");
        }
        return parts[2];
    }

    private Claims parseToken(String token, boolean requireIssuer) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        try {
            JwtParserBuilder parser = Jwts.parser().verifyWith(key);
            if (requireIssuer && issuer != null && !issuer.isBlank()) {
                parser = parser.requireIssuer(issuer);
            }
            return parser.build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, e.getMessage());
        }
    }

    private boolean isValid(String token, boolean requireIssuer) {
        try {
            parseToken(token, requireIssuer);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Long parseLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
