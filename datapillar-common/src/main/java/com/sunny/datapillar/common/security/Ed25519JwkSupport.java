package com.sunny.datapillar.common.security;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ed25519 JWK Tools. */
public final class Ed25519JwkSupport {

  private static final byte[] ED25519_SPKI_PREFIX =
      new byte[] {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
  private static final int RAW_PUBLIC_KEY_LENGTH = 32;
  private static final int RAW_PRIVATE_KEY_LENGTH = 32;
  private static final String KEY_TYPE_OKP = "OKP";
  private static final String CURVE_ED25519 = "Ed25519";
  private static final String ALGORITHM_EDDSA = "EdDSA";
  private static final String USE_SIGNATURE = "sig";

  private Ed25519JwkSupport() {}

  public static Map<String, Object> toJwk(String keyId, PublicKey publicKey) {
    if (keyId == null || keyId.isBlank()) {
      throw new IllegalArgumentException("keyId cannot be empty");
    }
    Map<String, Object> jwk = new LinkedHashMap<>();
    jwk.put("kty", KEY_TYPE_OKP);
    jwk.put("crv", CURVE_ED25519);
    jwk.put("alg", ALGORITHM_EDDSA);
    jwk.put("use", USE_SIGNATURE);
    jwk.put("kid", keyId.trim());
    jwk.put("x", encodePublicKeyToX(publicKey));
    return jwk;
  }

  public static Map<String, Object> toPrivateJwk(
      String keyId, PublicKey publicKey, PrivateKey privateKey) {
    if (privateKey == null) {
      throw new IllegalArgumentException("privateKey cannot be empty");
    }
    Map<String, Object> jwk = new LinkedHashMap<>(toJwk(keyId, publicKey));
    jwk.put("d", encodePrivateKeyToD(privateKey));
    return jwk;
  }

  public static PublicKey parseEd25519PublicKeyFromJwk(Map<?, ?> jwk) {
    if (jwk == null) {
      throw new IllegalArgumentException("jwk cannot be empty");
    }
    String kty = normalizeString(jwk.get("kty"));
    String crv = normalizeString(jwk.get("crv"));
    String x = normalizeString(jwk.get("x"));
    if (!KEY_TYPE_OKP.equals(kty)) {
      throw new IllegalArgumentException("jwk.kty must be OKP");
    }
    if (!CURVE_ED25519.equals(crv)) {
      throw new IllegalArgumentException("jwk.crv must be Ed25519");
    }
    if (x == null) {
      throw new IllegalArgumentException("jwk.x cannot be empty");
    }
    return decodePublicKeyFromX(x);
  }

  public static PrivateKey parseEd25519PrivateKeyFromJwk(Map<?, ?> jwk) {
    if (jwk == null) {
      throw new IllegalArgumentException("jwk cannot be empty");
    }
    String kty = normalizeString(jwk.get("kty"));
    String crv = normalizeString(jwk.get("crv"));
    String d = normalizeString(jwk.get("d"));
    if (!KEY_TYPE_OKP.equals(kty)) {
      throw new IllegalArgumentException("jwk.kty must be OKP");
    }
    if (!CURVE_ED25519.equals(crv)) {
      throw new IllegalArgumentException("jwk.crv must be Ed25519");
    }
    if (d == null) {
      throw new IllegalArgumentException("jwk.d cannot be empty");
    }
    return decodePrivateKeyFromD(d);
  }

  public static String encodePublicKeyToX(PublicKey publicKey) {
    if (publicKey == null) {
      throw new IllegalArgumentException("publicKey cannot be empty");
    }
    byte[] encoded = publicKey.getEncoded();
    if (encoded == null || encoded.length != ED25519_SPKI_PREFIX.length + RAW_PUBLIC_KEY_LENGTH) {
      throw new IllegalArgumentException("Ed25519 The public key encoding format is illegal");
    }
    for (int i = 0; i < ED25519_SPKI_PREFIX.length; i++) {
      if (encoded[i] != ED25519_SPKI_PREFIX[i]) {
        throw new IllegalArgumentException("Ed25519 The public key encoding format is illegal");
      }
    }
    byte[] raw = Arrays.copyOfRange(encoded, ED25519_SPKI_PREFIX.length, encoded.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  public static String encodePrivateKeyToD(PrivateKey privateKey) {
    if (privateKey == null) {
      throw new IllegalArgumentException("privateKey cannot be empty");
    }
    if (!(privateKey instanceof EdECPrivateKey edECPrivateKey)) {
      throw new IllegalArgumentException("privateKey must be Ed25519");
    }
    byte[] raw =
        edECPrivateKey
            .getBytes()
            .orElseThrow(() -> new IllegalArgumentException("privateKey raw bytes unavailable"));
    if (raw.length != RAW_PRIVATE_KEY_LENGTH) {
      throw new IllegalArgumentException("Ed25519 private key length is illegal");
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  public static PublicKey decodePublicKeyFromX(String x) {
    if (x == null || x.isBlank()) {
      throw new IllegalArgumentException("jwk.x cannot be empty");
    }
    byte[] raw;
    try {
      raw = Base64.getUrlDecoder().decode(x.trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("jwk.x Base64URL Decoding failed", ex);
    }
    if (raw.length != RAW_PUBLIC_KEY_LENGTH) {
      throw new IllegalArgumentException("jwk.x Illegal length");
    }

    byte[] der = new byte[ED25519_SPKI_PREFIX.length + raw.length];
    System.arraycopy(ED25519_SPKI_PREFIX, 0, der, 0, ED25519_SPKI_PREFIX.length);
    System.arraycopy(raw, 0, der, ED25519_SPKI_PREFIX.length, raw.length);
    try {
      KeyFactory keyFactory = KeyFactory.getInstance(CURVE_ED25519);
      return keyFactory.generatePublic(new X509EncodedKeySpec(der));
    } catch (Throwable ex) {
      throw new IllegalStateException("from JWK Build Ed25519 Public key failed", ex);
    }
  }

  public static PrivateKey decodePrivateKeyFromD(String d) {
    if (d == null || d.isBlank()) {
      throw new IllegalArgumentException("jwk.d cannot be empty");
    }
    byte[] raw;
    try {
      raw = Base64.getUrlDecoder().decode(d.trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("jwk.d Base64URL decoding failed", ex);
    }
    if (raw.length != RAW_PRIVATE_KEY_LENGTH) {
      throw new IllegalArgumentException("jwk.d illegal length");
    }
    try {
      KeyFactory keyFactory = KeyFactory.getInstance(CURVE_ED25519);
      return keyFactory.generatePrivate(new EdECPrivateKeySpec(NamedParameterSpec.ED25519, raw));
    } catch (Throwable ex) {
      throw new IllegalStateException("from JWK build Ed25519 private key failed", ex);
    }
  }

  private static String normalizeString(Object value) {
    if (!(value instanceof String text)) {
      return null;
    }
    String normalized = text.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
