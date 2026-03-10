package com.sunny.datapillar.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Shared helpers for API key hashing and display masking. */
public final class ApiKeyHashSupport {

  private ApiKeyHashSupport() {}

  public static String sha256(String apiKey) {
    String normalizedApiKey = normalize(apiKey);
    if (normalizedApiKey == null) {
      throw new IllegalArgumentException("apiKey must not be blank");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(normalizedApiKey.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hashed.length * 2);
      for (byte value : hashed) {
        builder.append(Character.forDigit((value >> 4) & 0xF, 16));
        builder.append(Character.forDigit(value & 0xF, 16));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
    }
  }

  public static String lastFour(String apiKey) {
    String normalized = normalize(apiKey);
    if (normalized == null) {
      throw new IllegalArgumentException("apiKey must not be blank");
    }
    return normalized.length() <= 4 ? normalized : normalized.substring(normalized.length() - 4);
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
