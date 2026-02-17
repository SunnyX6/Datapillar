package com.sunny.datapillar.gateway.util;

import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JWT工具类
 * 提供JWT通用工具能力
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class JwtUtil extends com.sunny.datapillar.common.utils.JwtUtil {

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.issuer}") String issuer) {
        super(secret, issuer);
    }

    @Override
    public Claims parseToken(String token) {
        return parseTokenWithIssuer(token);
    }

    @Override
    public boolean isValid(String token) {
        return isValidWithIssuer(token);
    }

    public List<String> getRoles(Claims claims) {
        return EdDsaJwtSupport.toStringList(claims.get("roles"));
    }
}
