package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import com.sunny.datapillar.common.security.GatewayAssertionClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthAssertionSignerTest {

    @TempDir
    Path tempDir;

    @Test
    void sign_shouldIncludeCoreClaims() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privateKeyPath = tempDir.resolve("auth-private.pem");
        Files.writeString(privateKeyPath, toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);

        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getGatewayAssertion().setEnabled(true);
        properties.getGatewayAssertion().setHeaderName("X-Gateway-Assertion");
        properties.getGatewayAssertion().setIssuer("datapillar-auth");
        properties.getGatewayAssertion().setAudience("datapillar-studio-service");
        properties.getGatewayAssertion().setTtlSeconds(20);
        properties.getGatewayAssertion().setKeyId("auth-test-kid");
        properties.getGatewayAssertion().setPrivateKeyPath(privateKeyPath.toString());

        AuthAssertionSigner signer = new AuthAssertionSigner(properties);
        String token = signer.sign(new AuthAssertionSigner.AssertionPayload(
                1L,
                10L,
                "tenant-acme",
                "sunny",
                "sunny@datapillar.test",
                List.of("ADMIN"),
                false,
                null,
                null,
                "POST",
                "/api/studio/projects"
        ));

        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("1", claims.getSubject());
        assertEquals("datapillar-auth", claims.getIssuer());
        assertTrue(EdDsaJwtSupport.hasAudience(claims, "datapillar-studio-service"));
        assertEquals(10L, ((Number) claims.get(GatewayAssertionClaims.TENANT_ID)).longValue());
        assertEquals("tenant-acme", claims.get(GatewayAssertionClaims.TENANT_CODE, String.class));
        assertEquals("POST", claims.get(GatewayAssertionClaims.METHOD, String.class));
        assertEquals("/api/studio/projects", claims.get(GatewayAssertionClaims.PATH, String.class));
        assertFalse(claims.getId().isBlank());
    }

    private String toPem(String type, byte[] derBytes) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(derBytes);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
