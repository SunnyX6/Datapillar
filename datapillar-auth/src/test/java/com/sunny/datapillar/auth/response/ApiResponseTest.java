package com.sunny.datapillar.auth.response;

import com.sunny.datapillar.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    private static final String TRACE_ID_KEY = "traceId";

    @AfterEach
    void tearDown() {
        MDC.remove(TRACE_ID_KEY);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldBuildSuccessResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put(TRACE_ID_KEY, "trace-1");

        ApiResponse<String> response = ApiResponse.ok("ok");

        assertEquals(200, response.getStatus());
        assertEquals("OK", response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertEquals("ok", response.getData());
        assertNotNull(response.getTimestamp());
        assertEquals("/test", response.getPath());
        assertEquals("trace-1", response.getTraceId());
        assertNull(response.getLimit());
        assertNull(response.getOffset());
        assertNull(response.getTotal());
    }

    @Test
    void shouldBuildPagedResponse() {
        ApiResponse<List<Integer>> response = ApiResponse.page(List.of(1, 2), 10, 0, 2);

        assertEquals(200, response.getStatus());
        assertEquals("OK", response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertEquals(List.of(1, 2), response.getData());
        assertEquals(10, response.getLimit());
        assertEquals(0, response.getOffset());
        assertEquals(2L, response.getTotal());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void shouldBuildErrorResponse() {
        ApiResponse<Object> response = ApiResponse.error(ErrorCode.INVALID_ARGUMENT, "参数错误");

        assertEquals(400, response.getStatus());
        assertEquals("INVALID_ARGUMENT", response.getCode());
        assertEquals("参数错误", response.getMessage());
        assertNull(response.getData());
        assertNotNull(response.getTimestamp());
    }
}
