package com.sunny.datapillar.auth.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenUtilTest {

    private static final String SECRET = "DatapillarJwtSecretKey123456789012345678901234567890";

    private JwtTokenUtil buildTokenUtil() {
        return new JwtTokenUtil(
                SECRET,
                60,
                604800,
                2592000,
                300,
                "datapillar-auth"
        );
    }

    @Test
    void generateRefreshToken_shouldUseDefaultExpirationWhenRememberMeFalse() {
        JwtTokenUtil jwtTokenUtil = buildTokenUtil();
        String token = jwtTokenUtil.generateRefreshToken(1L, 10L, false);

        Claims claims = jwtTokenUtil.parseToken(token);
        long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;

        assertEquals(jwtTokenUtil.getRefreshTokenExpiration(false), ttlSeconds);
    }

    @Test
    void generateRefreshToken_shouldUseRememberExpirationWhenRememberMeTrue() {
        JwtTokenUtil jwtTokenUtil = buildTokenUtil();
        String token = jwtTokenUtil.generateRefreshToken(1L, 10L, true);

        Claims claims = jwtTokenUtil.parseToken(token);
        long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;

        assertEquals(jwtTokenUtil.getRefreshTokenExpiration(true), ttlSeconds);
    }
}
