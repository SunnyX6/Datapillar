package com.sunny.datapillar.auth.handler;

import com.sunny.datapillar.common.constant.ErrorConstants;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerExceptionHandlerTest {

    private final AuthControllerExceptionHandler handler = new AuthControllerExceptionHandler();

    @Test
    void handleRuntimeException_shouldReturnMappedErrorResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        InternalException exception = new InternalException(
                new IllegalStateException("mysql_unreachable"),
                "db_connect_failed");

        ErrorResponse errorResponse = handler.handleDatapillarRuntimeException(exception, response);

        assertEquals(500, response.getStatus());
        assertEquals(ErrorConstants.INTERNAL_ERROR_CODE, errorResponse.getCode());
        assertEquals("InternalException", errorResponse.getType());
        assertEquals("db_connect_failed", errorResponse.getMessage());
        assertTrue(errorResponse.getStack() != null && !errorResponse.getStack().isEmpty());
    }

    @Test
    void handleException_shouldPreserveOriginalExceptionMessage() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Exception exception = new IllegalStateException("mysql_unreachable");

        ErrorResponse errorResponse = handler.handleException(exception, response);

        assertEquals(500, response.getStatus());
        assertEquals(ErrorConstants.INTERNAL_ERROR_CODE, errorResponse.getCode());
        assertEquals("IllegalStateException", errorResponse.getType());
        assertEquals("mysql_unreachable", errorResponse.getMessage());
    }
}
