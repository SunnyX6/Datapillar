package com.sunny.datapillar.auth.response;

import com.sunny.datapillar.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    @Test
    void shouldBuildSuccessResponse() {
        ApiResponse<String> response = ApiResponse.ok("ok");

        assertEquals(0, response.getCode());
        assertEquals("ok", response.getData());
        assertNull(response.getLimit());
        assertNull(response.getOffset());
        assertNull(response.getTotal());
    }

    @Test
    void shouldBuildPagedResponse() {
        ApiResponse<List<Integer>> response = ApiResponse.page(List.of(1, 2), 10, 0, 2);

        assertEquals(0, response.getCode());
        assertEquals(List.of(1, 2), response.getData());
        assertEquals(10, response.getLimit());
        assertEquals(0, response.getOffset());
        assertEquals(2L, response.getTotal());
    }

    @Test
    void shouldBuildEmptySuccessResponse() {
        ApiResponse<Void> response = ApiResponse.ok();

        assertEquals(0, response.getCode());
        assertNull(response.getData());
        assertNull(response.getLimit());
        assertNull(response.getOffset());
        assertNull(response.getTotal());
    }
}
