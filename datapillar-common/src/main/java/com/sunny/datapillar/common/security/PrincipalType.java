package com.sunny.datapillar.common.security;

/** Supported trusted principal types. */
public enum PrincipalType {
  USER(true),
  API_KEY(false);

  private final boolean requiresUserId;

  PrincipalType(boolean requiresUserId) {
    this.requiresUserId = requiresUserId;
  }

  public boolean requiresUserId() {
    return requiresUserId;
  }

  public static PrincipalType fromValue(String value) {
    String normalized = normalize(value);
    if (normalized == null) {
      return null;
    }
    for (PrincipalType principalType : values()) {
      if (principalType.name().equalsIgnoreCase(normalized)) {
        return principalType;
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
