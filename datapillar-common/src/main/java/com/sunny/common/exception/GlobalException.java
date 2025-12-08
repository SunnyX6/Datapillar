package com.sunny.common.exception;

import com.sunny.common.enums.GlobalSystemCode;
import lombok.Getter;

/**
 * 全局异常基类
 * 所有业务异常都应该继承此类
 *
 * @author Sunny
 * @since 2024-01-01
 */
@Getter
public class GlobalException extends RuntimeException {

    /**
     * 错误码
     */
    private final String code;

    /**
     * GlobalSystemCode枚举
     */
    private final GlobalSystemCode globalSystemCode;

    /**
     * 使用 GlobalSystemCode 枚举创建异常
     *
     * @param globalSystemCode 全局响应码枚举
     * @param args       消息参数
     */
    public GlobalException(GlobalSystemCode globalSystemCode, Object... args) {
        super(globalSystemCode.formatMessage(args));
        this.code = globalSystemCode.getCode();
        this.globalSystemCode = globalSystemCode;
    }

    /**
     * 使用 GlobalSystemCode 枚举创建异常(带原因)
     *
     * @param globalSystemCode 全局响应码枚举
     * @param cause      原因
     * @param args       消息参数
     */
    public GlobalException(GlobalSystemCode globalSystemCode, Throwable cause, Object... args) {
        super(globalSystemCode.formatMessage(args), cause);
        this.code = globalSystemCode.getCode();
        this.globalSystemCode = globalSystemCode;
    }
}
