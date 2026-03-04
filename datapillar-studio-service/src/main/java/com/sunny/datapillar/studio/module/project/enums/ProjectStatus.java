package com.sunny.datapillar.studio.module.project.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * ProjectStatusenumeration define projectStatusEnumeration values and business semantics
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Getter
public enum ProjectStatus {

  /** active state */
  ACTIVE("active", "active"),

  /** Archive status */
  ARCHIVED("archived", "Archive"),

  /** suspended state */
  PAUSED("paused", "pause"),

  /** delete status */
  DELETED("deleted", "Delete");

  /** status code(The database stores the value andJSONserialized value) */
  @EnumValue @JsonValue private final String code;

  /** Status description */
  private final String description;

  ProjectStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * According tocodeGet project status
   *
   * @param code status code
   * @return Project status enum
   */
  public static ProjectStatus fromCode(String code) {
    if (code == null) {
      return null;
    }
    for (ProjectStatus status : values()) {
      if (status.code.equals(code)) {
        return status;
      }
    }
    return null;
  }
}
