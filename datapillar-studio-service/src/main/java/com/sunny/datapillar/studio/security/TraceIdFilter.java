package com.sunny.datapillar.studio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.sunny.datapillar.common.constant.HeaderConstants;

import java.io.IOException;

/**
 * TraceId 透传过滤器
 */
@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(HeaderConstants.HEADER_TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
