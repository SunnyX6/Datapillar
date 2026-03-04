package com.sunny.datapillar.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ed25519JwkSupportTest {

  @Test
  void shouldEncodeAndDecodeEd25519PublicKey() throws Exception {
    KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    String x = Ed25519JwkSupport.encodePublicKeyToX(keyPair.getPublic());

    PublicKey parsed = Ed25519JwkSupport.decodePublicKeyFromX(x);

    assertEquals(keyPair.getPublic(), parsed);
  }

  @Test
  void shouldBuildAndParseEd25519Jwk() throws Exception {
    KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    Map<String, Object> jwk = Ed25519JwkSupport.toJwk("kid-1", keyPair.getPublic());

    PublicKey parsed = Ed25519JwkSupport.parseEd25519PublicKeyFromJwk(jwk);

    assertEquals(keyPair.getPublic(), parsed);
    assertEquals("kid-1", jwk.get("kid"));
    assertEquals("OKP", jwk.get("kty"));
    assertEquals("Ed25519", jwk.get("crv"));
  }

  @Test
  void shouldRejectNonOkpJwk() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                Ed25519JwkSupport.parseEd25519PublicKeyFromJwk(
                    Map.of(
                        "kty", "RSA",
                        "crv", "Ed25519",
                        "x", "AQ")));
    assertEquals("jwk.kty must be OKP", ex.getMessage());
  }
}
