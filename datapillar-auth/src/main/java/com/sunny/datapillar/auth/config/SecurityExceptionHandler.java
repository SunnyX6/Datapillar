package com.sunny.datapillar.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.auth.web.security.SecurityErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 统一安全异常响应
 */
@Slf4j
@Component
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException authException)
            throws IOException, ServletException {
        log.warn("未授权访问: {}", authException.getMessage());
        writeError(request, response, ErrorCode.AUTH_UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        log.warn("无权限访问: {}", accessDeniedException.getMessage());
        writeError(request, response, ErrorCode.AUTH_FORBIDDEN);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        SecurityErrorWriter.writeError(request, response, errorCode, objectMapper);
    }
}
