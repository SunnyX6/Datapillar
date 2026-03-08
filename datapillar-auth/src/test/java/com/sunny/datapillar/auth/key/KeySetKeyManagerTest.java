package com.sunny.datapillar.auth.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeySetKeyManagerTest {

  @TempDir Path tempDir;

  @Test
  void shouldLoadActiveKeyFromKeyset() {
    AuthProperties properties = new AuthProperties();
    properties.getToken().setKeysetPath("classpath:security/auth-token-dev-keyset.json");

    KeySetKeyManager manager = new KeySetKeyManager(new ObjectMapper(), properties);

    assertEquals("auth-dev-2026-01", manager.activeKid());
    assertEquals(1, manager.publicJwks().size());
    assertEquals("auth-dev-2026-01", manager.publicJwks().get(0).get("kid"));
  }

  @Test
  void shouldRejectMissingActiveKid() throws Exception {
    Path keysetPath = tempDir.resolve("invalid-keyset.json");
    Files.writeString(
        keysetPath,
        "{\"activeKid\":\"missing\",\"keys\":[{\"kid\":\"other\",\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"dZtzwS1gtdOLOtnnGEZ_4Q0gqRw6oT8kLbV4eQy2Wdg\",\"d\":\"QhkSi794yaYeuuP5Qb-lEE8JZcQzEcaq4P1JAWy0IdM\"}]}",
        StandardCharsets.UTF_8);

    AuthProperties properties = new AuthProperties();
    properties.getToken().setKeysetPath(keysetPath.toString());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> new KeySetKeyManager(new ObjectMapper(), properties));
    assertEquals("auth.token.keyset activeKid does not exist in keys", exception.getMessage());
  }
}
