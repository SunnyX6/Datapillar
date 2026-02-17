package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtTokenTest {

    private static final String SECRET = "DatapillarJwtSecretKey123456789012345678901234567890";

    private JwtUtil buildJwtUtil() {
        return new JwtUtil(SECRET, "datapillar-auth");
    }

    private JwtToken buildTokenUtil() {
        return new JwtToken(
                buildJwtUtil(),
                60,
                604800,
                2592000,
                300
        );
    }

    @Test
    void generateRefreshToken_shouldUseDefaultExpirationWhenRememberMeFalse() {
        JwtToken jwtToken = buildTokenUtil();
        JwtUtil jwtUtil = buildJwtUtil();
        String token = jwtToken.generateRefreshToken(1L, 10L, false);

        Claims claims = jwtUtil.parseToken(token);
        long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;

        assertEquals(jwtToken.getRefreshTokenExpiration(false), ttlSeconds);
        assertNotNull(jwtUtil.getSessionId(claims));
        assertNotNull(jwtUtil.getTokenId(claims));
    }

    @Test
    void generateRefreshToken_shouldUseRememberExpirationWhenRememberMeTrue() {
        JwtToken jwtToken = buildTokenUtil();
        JwtUtil jwtUtil = buildJwtUtil();
        String token = jwtToken.generateRefreshToken(1L, 10L, true);

        Claims claims = jwtUtil.parseToken(token);
        long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;

        assertEquals(jwtToken.getRefreshTokenExpiration(true), ttlSeconds);
        assertNotNull(jwtUtil.getSessionId(claims));
        assertNotNull(jwtUtil.getTokenId(claims));
    }
}
