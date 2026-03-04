package com.sunny.datapillar.common.crypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Set;
import java.util.logging.Logger;

/** Ed25519 key pair generator. */
public final class Ed25519KeyPairGenerator {

  private static final Logger LOGGER = Logger.getLogger(Ed25519KeyPairGenerator.class.getName());
  private static final String ALGORITHM = "Ed25519";
  private static final String DEFAULT_OUTPUT_DIR = "config/security/local";
  private static final String DEFAULT_KEY_PREFIX = "gateway-assertion-dev";
  private static final Set<PosixFilePermission> PRIVATE_KEY_PERMISSIONS =
      PosixFilePermissions.fromString("rw-------");

  private Ed25519KeyPairGenerator() {}

  public static KeyPair generateEd25519KeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
      return generator.generateKeyPair();
    } catch (Throwable ex) {
      throw new IllegalStateException("generate Ed25519 Key pair failed", ex);
    }
  }

  public static String toPublicKeyPem(PublicKey key) {
    if (key == null) {
      throw new IllegalArgumentException("Public key cannot be empty");
    }
    return toPem("PUBLIC KEY", key.getEncoded());
  }

  public static String toPrivateKeyPem(PrivateKey key) {
    if (key == null) {
      throw new IllegalArgumentException("Private key cannot be empty");
    }
    return toPem("PRIVATE KEY", key.getEncoded());
  }

  public static void main(String[] args) {
    String outputDirArg = args.length > 0 ? args[0] : DEFAULT_OUTPUT_DIR;
    String keyPrefixArg = args.length > 1 ? args[1] : DEFAULT_KEY_PREFIX;
    Path outputDir = Path.of(outputDirArg).toAbsolutePath().normalize();
    String keyPrefix = normalizeKeyPrefix(keyPrefixArg);
    Path privateKeyPath = outputDir.resolve(keyPrefix + "-private.pem");
    Path publicKeyPath = outputDir.resolve(keyPrefix + "-public.pem");
    KeyPair keyPair = generateEd25519KeyPair();
    try {
      Files.createDirectories(outputDir);
      Files.writeString(
          privateKeyPath, toPrivateKeyPem(keyPair.getPrivate()), StandardCharsets.US_ASCII);
      setPrivateKeyPermission(privateKeyPath);
      Files.writeString(
          publicKeyPath, toPublicKeyPem(keyPair.getPublic()), StandardCharsets.US_ASCII);
    } catch (IOException ex) {
      throw new IllegalStateException("write Ed25519 Key file failed", ex);
    }

    LOGGER.info(() -> "Gateway Assertion Key generation completed:");
    LOGGER.info(() -> " private:" + privateKeyPath);
    LOGGER.info(() -> " public:" + publicKeyPath);
  }

  private static void setPrivateKeyPermission(Path privateKeyPath) {
    try {
      Files.setPosixFilePermissions(privateKeyPath, PRIVATE_KEY_PERMISSIONS);
    } catch (UnsupportedOperationException | IOException ex) {
      LOGGER.warning(
          "Failed to set private key file permissions,It is recommended to perform it manually"
              + " chmod 600:"
              + privateKeyPath);
    }
  }

  private static String normalizeKeyPrefix(String keyPrefix) {
    if (keyPrefix == null) {
      throw new IllegalArgumentException("keyPrefix cannot be empty");
    }
    String normalized = keyPrefix.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("keyPrefix cannot be empty");
    }
    return normalized;
  }

  private static String toPem(String type, byte[] derBytes) {
    String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(derBytes);
    return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
  }
}
