package com.sunny.datapillar.studio.module.llm.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * AIModelTypeenumeration definitionAIModelTypeEnumeration values and business semantics
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Getter
public enum AiModelType {
  CHAT("chat", "dialogue model"),
  EMBEDDINGS("embeddings", "vector model"),
  RERANKING("reranking", "rearrange model"),
  CODE("code", "code model");

  @EnumValue @JsonValue private final String code;

  private final String description;

  AiModelType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static AiModelType fromCode(String code) {
    if (code == null) {
      return null;
    }
    for (AiModelType type : values()) {
      if (type.code.equalsIgnoreCase(code)) {
        return type;
      }
    }
    return null;
  }
}
