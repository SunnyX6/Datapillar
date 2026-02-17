package com.sunny.datapillar.studio.module.llm.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * AIModelType枚举
 * 定义AIModelType枚举取值与业务语义
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Getter
public enum AiModelType {

    CHAT("chat", "对话模型"),
    EMBEDDINGS("embeddings", "向量模型"),
    RERANKING("reranking", "重排模型"),
    CODE("code", "代码模型");

    @EnumValue
    @JsonValue
    private final String code;

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
