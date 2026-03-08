package com.sunny.datapillar.auth.key;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.common.security.Ed25519JwkSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Key manager backed by a single Ed25519 keyset document. */
@Component
public class KeySetKeyManager implements KeyManager {

  private static final String CLASSPATH_PREFIX = "classpath:";

  private final String activeKid;
  private final PublicKey publicKey;
  private final PrivateKey privateKey;
  private final List<Map<String, Object>> publicJwks;

  public KeySetKeyManager(ObjectMapper objectMapper, AuthProperties authProperties) {
    AuthProperties.Token token = authProperties.getToken();
    KeySetDocument document = loadKeySet(objectMapper, token.getKeysetPath());
    if (document.activeKid() == null || document.activeKid().isBlank()) {
      throw new IllegalStateException("auth.token.keyset activeKid cannot be empty");
    }
    if (document.keys() == null || document.keys().isEmpty()) {
      throw new IllegalStateException("auth.token.keyset keys cannot be empty");
    }

    KeyMaterial activeMaterial = null;
    List<Map<String, Object>> jwks = new ArrayList<>();
    for (Map<String, Object> rawKey : document.keys()) {
      validateJwk(rawKey);
      String kid = requireText(rawKey.get("kid"), "auth.token.keyset key kid cannot be empty");
      PublicKey parsedPublicKey = Ed25519JwkSupport.parseEd25519PublicKeyFromJwk(rawKey);
      PrivateKey parsedPrivateKey = Ed25519JwkSupport.parseEd25519PrivateKeyFromJwk(rawKey);
      jwks.add(Ed25519JwkSupport.toJwk(kid, parsedPublicKey));
      if (document.activeKid().trim().equals(kid)) {
        activeMaterial = new KeyMaterial(kid, parsedPublicKey, parsedPrivateKey);
      }
    }

    if (activeMaterial == null) {
      throw new IllegalStateException("auth.token.keyset activeKid does not exist in keys");
    }

    this.activeKid = activeMaterial.kid();
    this.publicKey = activeMaterial.publicKey();
    this.privateKey = activeMaterial.privateKey();
    this.publicJwks = List.copyOf(jwks);
  }

  @Override
  public String activeKid() {
    return activeKid;
  }

  @Override
  public PublicKey publicKey() {
    return publicKey;
  }

  @Override
  public PrivateKey privateKey() {
    return privateKey;
  }

  @Override
  public List<Map<String, Object>> publicJwks() {
    return publicJwks;
  }

  private KeySetDocument loadKeySet(ObjectMapper objectMapper, String path) {
    try {
      String json = readText(path);
      Map<String, Object> payload =
          objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
      String activeKid = payload.get("activeKid") instanceof String value ? value.trim() : null;
      List<Map<String, Object>> keys = new ArrayList<>();
      if (payload.get("keys") instanceof List<?> rawKeys) {
        for (Object rawKey : rawKeys) {
          if (rawKey instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
              if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
              }
            }
            keys.add(normalized);
          }
        }
      }
      return new KeySetDocument(activeKid, List.copyOf(keys));
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read signing keyset: " + path, ex);
    }
  }

  private void validateJwk(Map<String, Object> jwk) {
    if (!"sig".equals(requireText(jwk.get("use"), "auth.token.keyset key use must be sig"))) {
      throw new IllegalStateException("auth.token.keyset key use must be sig");
    }
    if (!"EdDSA".equals(requireText(jwk.get("alg"), "auth.token.keyset key alg must be EdDSA"))) {
      throw new IllegalStateException("auth.token.keyset key alg must be EdDSA");
    }
  }

  private String requireText(Object value, String message) {
    if (!(value instanceof String text)) {
      throw new IllegalStateException(message);
    }
    String normalized = text.trim();
    if (normalized.isEmpty()) {
      throw new IllegalStateException(message);
    }
    return normalized;
  }

  private String readText(String path) throws IOException {
    if (path.startsWith(CLASSPATH_PREFIX)) {
      String resource = path.substring(CLASSPATH_PREFIX.length());
      while (resource.startsWith("/")) {
        resource = resource.substring(1);
      }
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      if (classLoader == null) {
        classLoader = KeySetKeyManager.class.getClassLoader();
      }
      try (InputStream inputStream = classLoader.getResourceAsStream(resource)) {
        if (inputStream == null) {
          throw new IOException("classpath resource does not exist: " + resource);
        }
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
    return Files.readString(Path.of(path), StandardCharsets.UTF_8);
  }

  private record KeyMaterial(String kid, PublicKey publicKey, PrivateKey privateKey) {}

  private record KeySetDocument(String activeKid, List<Map<String, Object>> keys) {}
}
