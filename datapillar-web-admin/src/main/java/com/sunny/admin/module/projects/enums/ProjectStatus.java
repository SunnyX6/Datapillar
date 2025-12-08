package com.sunny.admin.module.projects.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 项目状态枚举
 *
 * @author sunny
 * @since 2024-11-08
 */
@Getter
public enum ProjectStatus {

    /**
     * 活跃状态
     */
    ACTIVE("active", "活跃"),

    /**
     * 归档状态
     */
    ARCHIVED("archived", "归档"),

    /**
     * 暂停状态
     */
    PAUSED("paused", "暂停"),

    /**
     * 删除状态
     */
    DELETED("deleted", "删除");

    /**
     * 状态码(数据库存储值和JSON序列化值)
     */
    @EnumValue
    @JsonValue
    private final String code;

    /**
     * 状态描述
     */
    private final String description;

    ProjectStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取项目状态
     *
     * @param code 状态码
     * @return 项目状态枚举
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
