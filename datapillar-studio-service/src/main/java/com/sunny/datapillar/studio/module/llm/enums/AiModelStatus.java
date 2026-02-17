package com.sunny.datapillar.studio.module.llm.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * AIModelStatus枚举
 * 定义AIModelStatus枚举取值与业务语义
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Getter
public enum AiModelStatus {

    CONNECT("CONNECT", "未连接"),
    ACTIVE("ACTIVE", "已连接");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    AiModelStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
