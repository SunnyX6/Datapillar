package com.sunny.datapillar.studio.module.setup.enums;

/**
 * Setup bootstrap status enumeration Defines setup state machine values for bootstrap persistence
 *
 * @author Sunny
 * @date 2026-03-07
 */
public enum SetupBootstrapStatus {
  PENDING(0),
  PROVISIONING(1),
  FAILED(2),
  COMPLETED(3);

  private final int code;

  SetupBootstrapStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  public boolean matches(Integer value) {
    return value != null && value == code;
  }

  public static SetupBootstrapStatus fromCode(Integer value) {
    if (value != null) {
      for (SetupBootstrapStatus status : values()) {
        if (status.code == value) {
          return status;
        }
      }
    }
    return PENDING;
  }
}
