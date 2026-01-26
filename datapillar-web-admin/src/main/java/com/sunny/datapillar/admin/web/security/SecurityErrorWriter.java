package com.sunny.datapillar.admin.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.admin.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * 统一安全异常输出
 */
public final class SecurityErrorWriter {

    private static final String TRACE_ID_KEY = "traceId";

    private SecurityErrorWriter() {
    }

    public static void writeError(HttpServletRequest request, HttpServletResponse response,
                                  ErrorCode errorCode, ObjectMapper objectMapper) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Object> body = ApiResponse.error(errorCode);
        body.setPath(request.getRequestURI());
        body.setTraceId(MDC.get(TRACE_ID_KEY));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
