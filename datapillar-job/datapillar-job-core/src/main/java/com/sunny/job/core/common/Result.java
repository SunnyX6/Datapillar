package com.sunny.job.core.common;

import java.io.Serializable;

/**
 * 统一返回结果
 *
 * @param <T> 数据类型
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int code;
    private final String message;
    private final T data;
    private final long timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> fail() {
        return new Result<>(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMessage(), null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.FAIL.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Result{code=" + code + ", message='" + message + "', data=" + data + ", timestamp=" + timestamp + '}';
    }

    /**
     * 结果状态码
     */
    public enum ResultCode {
        SUCCESS(0, "成功"),
        FAIL(1, "失败"),

        PARAM_ERROR(400, "参数错误"),
        UNAUTHORIZED(401, "未授权"),
        FORBIDDEN(403, "禁止访问"),
        NOT_FOUND(404, "资源不存在"),

        JOB_NOT_FOUND(1001, "任务不存在"),
        JOB_DISABLED(1002, "任务已禁用"),
        JOB_RUNNING(1003, "任务正在运行"),

        WORKFLOW_NOT_FOUND(2001, "工作流不存在"),
        WORKFLOW_DISABLED(2002, "工作流已禁用"),
        WORKFLOW_CYCLE_DETECTED(2003, "工作流存在循环依赖"),

        WORKER_NOT_AVAILABLE(3001, "没有可用的 Worker"),
        WORKER_TIMEOUT(3002, "Worker 执行超时"),
        WORKER_NOT_FOUND(3003, "Worker 不存在"),

        LEASE_ACQUIRE_FAILED(4001, "获取租约失败"),
        LEASE_EXPIRED(4002, "租约已过期"),

        INTERNAL_ERROR(5000, "内部错误");

        private final int code;
        private final String message;

        ResultCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
