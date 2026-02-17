package com.sunny.datapillar.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Api响应模型
 * 定义Api响应数据结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private static final int SUCCESS_CODE = 0;

    private int code;
    private T data;
    private Integer limit;
    private Integer offset;
    private Long total;

    public ApiResponse() {
    }

    public ApiResponse(int code, T data, Integer limit, Integer offset, Long total) {
        this.code = code;
        this.data = data;
        this.limit = limit;
        this.offset = offset;
        this.total = total;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
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

    private static <T> ApiResponse<T> buildSuccess(T data, Integer limit, Integer offset, Long total) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = SUCCESS_CODE;
        response.data = data;
        response.limit = limit;
        response.offset = offset;
        response.total = total;
        return response;
    }
}

