package com.sunny.datapillar.workbench.web.response;

import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class RequestContextUtil {

    private static final String TRACE_ID_KEY = "traceId";

    private RequestContextUtil() {
    }

    static String getPath() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null || attributes.getRequest() == null) {
            return null;
        }
        return attributes.getRequest().getRequestURI();
    }

    static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            return null;
        }
        return traceId;
    }
}
