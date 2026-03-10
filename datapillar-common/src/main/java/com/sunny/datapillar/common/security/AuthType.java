package com.sunny.datapillar.common.security;

/** Supported platform authentication types. */
public enum AuthType {
  JWT,
  API_KEY;

  public static AuthType fromValue(String value) {
    String normalized = normalize(value);
    if (normalized == null) {
      return null;
    }
    for (AuthType authType : values()) {
      if (authType.name().equalsIgnoreCase(normalized)) {
        return authType;
      }
    }
    return null;
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
