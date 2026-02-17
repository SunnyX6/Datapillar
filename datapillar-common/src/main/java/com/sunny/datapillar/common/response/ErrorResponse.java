package com.sunny.datapillar.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 错误响应模型
 * 定义错误响应数据结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int code;
    private String type;
    private String message;
    private List<String> stack;

    public ErrorResponse() {
    }

    public ErrorResponse(int code, String type, String message, List<String> stack) {
        this.code = code;
        this.type = type;
        this.message = message;
        this.stack = stack;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getStack() {
        return stack;
    }

    public void setStack(List<String> stack) {
        this.stack = stack;
    }

    public static ErrorResponse of(int code, String type, String message, List<String> stack) {
        return new ErrorResponse(code, type, message, stack);
    }
}

