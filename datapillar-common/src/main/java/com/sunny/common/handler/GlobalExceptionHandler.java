package com.sunny.common.handler;

import com.sunny.common.exception.GlobalException;
import com.sunny.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局异常处理器基类
 * 提供统一的异常处理逻辑,各模块继承并扩展
 *
 * 使用方式:
 * 1. 在业务模块继承此类
 * 2. 添加 @RestControllerAdvice 注解
 * 3. 重写 getLogger() 方法返回模块自己的 logger
 * 4. 可选择性覆盖方法以自定义行为
 *
 * @author Sunny
 * @since 2024-01-01
 */
public abstract class GlobalExceptionHandler {

    /**
     * 子类必须提供自己的 Logger
     */
    protected abstract Logger getLogger();

    /**
     * 处理 GlobalException
     */
    public ApiResponse<Object> handleGlobalException(GlobalException e) {
        getLogger().warn("Global exception occurred: {}", e.getMessage(), e);

        return ApiResponse.error(
            e.getCode() != null ? e.getCode() : "GLOBAL_ERROR",
            e.getMessage()
        );
    }

    /**
     * 处理参数校验异常
     */
    public ApiResponse<Object> handleValidationException(Exception e) {
        getLogger().warn("Validation failed: {}", e.getMessage());

        return ApiResponse.error(
            "VALIDATION_ERROR",
            "参数校验失败: " + e.getMessage()
        );
    }

    /**
     * 处理未捕获的异常
     * 注意: 只捕获 Exception,不捕获 RuntimeException 避免重叠
     */
    public ApiResponse<Object> handleException(Exception e) {
        getLogger().error("Unhandled exception occurred: {}", e.getMessage(), e);

        return ApiResponse.error(
            "INTERNAL_SERVER_ERROR",
            e.getMessage() != null ? e.getMessage() : "服务器内部错误"
        );
    }
}
