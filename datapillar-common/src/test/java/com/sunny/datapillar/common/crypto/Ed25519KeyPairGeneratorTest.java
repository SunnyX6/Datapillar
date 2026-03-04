package com.sunny.datapillar.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Ed25519KeyPairGeneratorTest {

  @TempDir Path tempDir;

  @Test
  void shouldGenerateValidEd25519PemPair() throws Exception {
    KeyPair keyPair = Ed25519KeyPairGenerator.generateEd25519KeyPair();
    assertNotNull(keyPair.getPrivate());
    assertNotNull(keyPair.getPublic());

    String privatePem = Ed25519KeyPairGenerator.toPrivateKeyPem(keyPair.getPrivate());
    String publicPem = Ed25519KeyPairGenerator.toPublicKeyPem(keyPair.getPublic());

    assertTrue(privatePem.contains("BEGIN PRIVATE KEY"));
    assertTrue(publicPem.contains("BEGIN PUBLIC KEY"));

    var parsedPrivate = EdDsaJwtSupport.parsePrivateKey(privatePem);
    var parsedPublic = EdDsaJwtSupport.parsePublicKey(publicPem);
    assertEquals("EdDSA", parsedPrivate.getAlgorithm());
    assertEquals("EdDSA", parsedPublic.getAlgorithm());
  }

  @Test
  void mainShouldWritePemFilesToTargetDirectory() throws Exception {
    Path outputDir = tempDir.resolve("keys");
    Ed25519KeyPairGenerator.main(new String[] {outputDir.toString(), "gateway-assertion-dev"});

    Path privateKeyPath = outputDir.resolve("gateway-assertion-dev-private.pem");
    Path publicKeyPath = outputDir.resolve("gateway-assertion-dev-public.pem");
    assertTrue(Files.exists(privateKeyPath));
    assertTrue(Files.exists(publicKeyPath));

    String privatePem = Files.readString(privateKeyPath, StandardCharsets.US_ASCII);
    String publicPem = Files.readString(publicKeyPath, StandardCharsets.US_ASCII);
    assertTrue(privatePem.contains("BEGIN PRIVATE KEY"));
    assertTrue(publicPem.contains("BEGIN PUBLIC KEY"));
  }
}
