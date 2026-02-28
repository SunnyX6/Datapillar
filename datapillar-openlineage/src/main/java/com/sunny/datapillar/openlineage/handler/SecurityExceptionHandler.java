package com.sunny.datapillar.openlineage.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.ExceptionMapper;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.response.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 安全异常处理器。
 */
@Slf4j
@Component
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException authException)
            throws IOException, ServletException {
        writeError(response, new UnauthorizedException(authException, "未授权访问"));
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        writeError(response, new ForbiddenException(accessDeniedException, "无权限访问"));
    }

    public void writeError(HttpServletResponse response, RuntimeException exception) throws IOException {
        ExceptionMapper.ExceptionDetail detail = ExceptionMapper.resolve(exception);
        response.setStatus(detail.httpStatus());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        ErrorResponse body = ErrorResponse.of(detail.errorCode(), detail.type(), detail.message(), detail.traceId());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
