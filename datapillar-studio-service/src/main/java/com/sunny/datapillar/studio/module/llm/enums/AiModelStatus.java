package com.sunny.datapillar.studio.module.llm.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * AIModelStatusenumeration definitionAIModelStatusEnumeration values and business semantics
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Getter
public enum AiModelStatus {
  CONNECT("CONNECT", "Not connected"),
  ACTIVE("ACTIVE", "Connected");

  @EnumValue @JsonValue private final String code;

  private final String description;

  AiModelStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }
}
