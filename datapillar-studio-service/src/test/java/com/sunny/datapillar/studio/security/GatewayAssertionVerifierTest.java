package com.sunny.datapillar.studio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.security.GatewayAssertionClaims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayAssertionVerifierTest {

    private static final String KID = "gw-test-kid";
    private static final String PREVIOUS_KID = "gw-previous-kid";

    @TempDir
    Path tempDir;

    @Test
    void verify_shouldParseValidAssertion() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path publicPath = tempDir.resolve("gw-public.pem");
        Files.writeString(publicPath, toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);

        GatewayAssertionProperties properties = new GatewayAssertionProperties();
        properties.setEnabled(true);
        properties.setIssuer("datapillar-gateway");
        properties.setAudience("datapillar-studio-service");
        properties.setKeyId(KID);
        properties.setPublicKeyPath(publicPath.toString());

        GatewayAssertionVerifier verifier = new GatewayAssertionVerifier(properties, new ObjectMapper());

        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject("1")
                .issuer("datapillar-gateway")
                .id("jti-1")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(30)))
                .claim(GatewayAssertionClaims.AUDIENCE, "datapillar-studio-service")
                .claim(GatewayAssertionClaims.TENANT_ID, 10L)
                .claim(GatewayAssertionClaims.USERNAME, "sunny")
                .claim(GatewayAssertionClaims.EMAIL, "sunny@datapillar.test")
                .claim(GatewayAssertionClaims.ROLES, List.of("ADMIN"))
                .claim(GatewayAssertionClaims.METHOD, "GET")
                .claim(GatewayAssertionClaims.PATH, "/api/studio/projects")
                .signWith(keyPair.getPrivate(), Jwts.SIG.EdDSA)
                .compact();

        GatewayAssertionContext context = verifier.verify(token, "GET", "/api/studio/projects");

        assertEquals(1L, context.userId());
        assertEquals(10L, context.tenantId());
        assertEquals("sunny", context.username());
        assertEquals(List.of("ADMIN"), context.roles());
    }

    @Test
    void verify_shouldRejectPathMismatch() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path publicPath = tempDir.resolve("gw-public-2.pem");
        Files.writeString(publicPath, toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);

        GatewayAssertionProperties properties = new GatewayAssertionProperties();
        properties.setEnabled(true);
        properties.setIssuer("datapillar-gateway");
        properties.setAudience("datapillar-studio-service");
        properties.setKeyId(KID);
        properties.setPublicKeyPath(publicPath.toString());

        GatewayAssertionVerifier verifier = new GatewayAssertionVerifier(properties, new ObjectMapper());

        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject("1")
                .issuer("datapillar-gateway")
                .id("jti-2")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(30)))
                .claim(GatewayAssertionClaims.AUDIENCE, "datapillar-studio-service")
                .claim(GatewayAssertionClaims.TENANT_ID, 10L)
                .claim(GatewayAssertionClaims.METHOD, "GET")
                .claim(GatewayAssertionClaims.PATH, "/api/studio/projects")
                .signWith(keyPair.getPrivate(), Jwts.SIG.EdDSA)
                .compact();

        assertThrows(RuntimeException.class, () -> verifier.verify(token, "GET", "/api/studio/users"));
    }

    @Test
    void verify_shouldRejectAudienceMismatch() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path publicPath = tempDir.resolve("gw-public-3.pem");
        Files.writeString(publicPath, toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);

        GatewayAssertionProperties properties = new GatewayAssertionProperties();
        properties.setEnabled(true);
        properties.setIssuer("datapillar-gateway");
        properties.setAudience("datapillar-studio-service");
        properties.setKeyId(KID);
        properties.setPublicKeyPath(publicPath.toString());

        GatewayAssertionVerifier verifier = new GatewayAssertionVerifier(properties, new ObjectMapper());

        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject("1")
                .issuer("datapillar-gateway")
                .id("jti-3")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(30)))
                .claim(GatewayAssertionClaims.AUDIENCE, "wrong-audience")
                .claim(GatewayAssertionClaims.TENANT_ID, 10L)
                .claim(GatewayAssertionClaims.METHOD, "GET")
                .claim(GatewayAssertionClaims.PATH, "/api/studio/projects")
                .signWith(keyPair.getPrivate(), Jwts.SIG.EdDSA)
                .compact();

        assertThrows(RuntimeException.class, () -> verifier.verify(token, "GET", "/api/studio/projects"));
    }

    @Test
    void verify_shouldRejectExpiredAssertion() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path publicPath = tempDir.resolve("gw-public-4.pem");
        Files.writeString(publicPath, toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);

        GatewayAssertionProperties properties = new GatewayAssertionProperties();
        properties.setEnabled(true);
        properties.setIssuer("datapillar-gateway");
        properties.setAudience("datapillar-studio-service");
        properties.setKeyId(KID);
        properties.setPublicKeyPath(publicPath.toString());

        GatewayAssertionVerifier verifier = new GatewayAssertionVerifier(properties, new ObjectMapper());

        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject("1")
                .issuer("datapillar-gateway")
                .id("jti-4")
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(Instant.now().minusSeconds(1)))
                .claim(GatewayAssertionClaims.AUDIENCE, "datapillar-studio-service")
                .claim(GatewayAssertionClaims.TENANT_ID, 10L)
                .claim(GatewayAssertionClaims.METHOD, "GET")
                .claim(GatewayAssertionClaims.PATH, "/api/studio/projects")
                .signWith(keyPair.getPrivate(), Jwts.SIG.EdDSA)
                .compact();

        assertThrows(RuntimeException.class, () -> verifier.verify(token, "GET", "/api/studio/projects"));
    }

    @Test
    void verify_shouldRejectInvalidSignature() throws Exception {
        KeyPair verifyKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair signKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path publicPath = tempDir.resolve("gw-public-5.pem");
        Files.writeString(publicPath, toPem("PUBLIC KEY", verifyKeyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);

        GatewayAssertionProperties properties = new GatewayAssertionProperties();
        properties.setEnabled(true);
        properties.setIssuer("datapillar-gateway");
        properties.setAudience("datapillar-studio-service");
        properties.setKeyId(KID);
        properties.setPublicKeyPath(publicPath.toString());

        GatewayAssertionVerifier verifier = new GatewayAssertionVerifier(properties, new ObjectMapper());

        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject("1")
                .issuer("datapillar-gateway")
                .id("jti-5")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(30)))
                .claim(GatewayAssertionClaims.AUDIENCE, "datapillar-studio-service")
                .claim(GatewayAssertionClaims.TENANT_ID, 10L)
                .claim(GatewayAssertionClaims.METHOD, "GET")
                .claim(GatewayAssertionClaims.PATH, "/api/studio/projects")
                .signWith(signKeyPair.getPrivate(), Jwts.SIG.EdDSA)
                .compact();

        assertThrows(RuntimeException.class, () -> verifier.verify(token, "GET", "/api/studio/projects"));
    }

    @Test
    void verify_shouldAcceptPreviousKeyDuringRotationWindow() throws Exception {
        KeyPair primaryKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair previousKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path primaryPublicPath = tempDir.resolve("gw-public-primary.pem");
        Path previousPublicPath = tempDir.resolve("gw-public-previous.pem");
        Files.writeString(primaryPublicPath, toPem("PUBLIC KEY", primaryKeyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);
        Files.writeString(previousPublicPath, toPem("PUBLIC KEY", previousKeyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);

        GatewayAssertionProperties properties = new GatewayAssertionProperties();
        properties.setEnabled(true);
        properties.setIssuer("datapillar-gateway");
        properties.setAudience("datapillar-studio-service");
        properties.setKeyId(KID);
        properties.setPublicKeyPath(primaryPublicPath.toString());
        properties.setPreviousKeyId(PREVIOUS_KID);
        properties.setPreviousPublicKeyPath(previousPublicPath.toString());

        GatewayAssertionVerifier verifier = new GatewayAssertionVerifier(properties, new ObjectMapper());

        String token = Jwts.builder()
                .header().keyId(PREVIOUS_KID).and()
                .subject("1")
                .issuer("datapillar-gateway")
                .id("jti-6")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(30)))
                .claim(GatewayAssertionClaims.AUDIENCE, "datapillar-studio-service")
                .claim(GatewayAssertionClaims.TENANT_ID, 10L)
                .claim(GatewayAssertionClaims.METHOD, "GET")
                .claim(GatewayAssertionClaims.PATH, "/api/studio/projects")
                .signWith(previousKeyPair.getPrivate(), Jwts.SIG.EdDSA)
                .compact();

        GatewayAssertionContext context = verifier.verify(token, "GET", "/api/studio/projects");
        assertEquals(1L, context.userId());
    }

    private String toPem(String type, byte[] derBytes) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(derBytes);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
