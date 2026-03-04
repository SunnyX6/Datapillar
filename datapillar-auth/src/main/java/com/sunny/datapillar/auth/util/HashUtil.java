package com.sunny.datapillar.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility methods for hash operations.
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class HashUtil {

  private HashUtil() {}

  public static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hashed.length * 2);
      for (byte b : hashed) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
    }
  }
}
