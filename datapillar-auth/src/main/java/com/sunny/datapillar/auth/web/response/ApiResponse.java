package com.sunny.datapillar.auth.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sunny.datapillar.common.error.ErrorCode;

import java.time.Instant;

/**
 * 统一响应结构
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private static final int SUCCESS_STATUS = 200;
    private static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_MESSAGE = "操作成功";

    private int status;
    private String code;
    private String message;
    private T data;
    private String timestamp;
    private String path;
    private String traceId;
    private Integer limit;
    private Integer offset;
    private Long total;

    public ApiResponse() {
    }

    public ApiResponse(int status, String code, String message, T data, String timestamp, String path,
                       String traceId, Integer limit, Integer offset, Long total) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
        this.path = path;
        this.traceId = traceId;
        this.limit = limit;
        this.offset = offset;
        this.total = total;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return buildSuccess(data, null, null, null);
    }

    public static ApiResponse<Void> ok() {
        return buildSuccess(null, null, null, null);
    }

    public static <T> ApiResponse<T> page(T data, int limit, int offset, long total) {
        return buildSuccess(data, limit, offset, total);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return buildError(errorCode, errorCode.getMessageTemplate());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return buildError(errorCode, message);
    }

    private static <T> ApiResponse<T> buildSuccess(T data, Integer limit, Integer offset, Long total) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = SUCCESS_STATUS;
        response.code = SUCCESS_CODE;
        response.message = SUCCESS_MESSAGE;
        response.data = data;
        response.timestamp = Instant.now().toString();
        response.path = RequestContextUtil.getPath();
        response.traceId = RequestContextUtil.getTraceId();
        response.limit = limit;
        response.offset = offset;
        response.total = total;
        return response;
    }

    private static <T> ApiResponse<T> buildError(ErrorCode errorCode, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = errorCode.getHttpStatus();
        response.code = errorCode.getCode();
        response.message = message;
        response.data = null;
        response.timestamp = Instant.now().toString();
        response.path = RequestContextUtil.getPath();
        response.traceId = RequestContextUtil.getTraceId();
        return response;
    }
}
