package com.sunny.job.core.enums;

/**
 * 告警渠道类型
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public enum AlarmChannelType {

    DINGTALK(1, "钉钉"),
    WECOM(2, "企业微信"),
    FEISHU(3, "飞书"),
    WEBHOOK(4, "Webhook"),
    EMAIL(5, "邮件");

    private final int code;
    private final String desc;

    AlarmChannelType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static AlarmChannelType of(int code) {
        for (AlarmChannelType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的告警渠道类型: " + code);
    }
}
