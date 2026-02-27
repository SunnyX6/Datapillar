package com.sunny.datapillar.auth.dto.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.dto.login.request.LoginRequest;
import com.sunny.datapillar.auth.dto.login.response.LoginResultResponse;
import com.sunny.datapillar.auth.dto.login.response.TenantOptionItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginRequestShouldKeepJsonFieldContract() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setStage("AUTH");
        request.setRememberMe(true);
        request.setLoginAlias("sunny");
        request.setPassword("123456");
        request.setTenantCode("demo");
        request.setProvider("dingtalk");
        request.setCode("code-1");
        request.setState("state-1");
        request.setTenantId(10L);

        JsonNode jsonNode = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertTrue(jsonNode.has("stage"));
        assertTrue(jsonNode.has("rememberMe"));
        assertTrue(jsonNode.has("loginAlias"));
        assertTrue(jsonNode.has("password"));
        assertTrue(jsonNode.has("tenantCode"));
        assertTrue(jsonNode.has("provider"));
        assertTrue(jsonNode.has("code"));
        assertTrue(jsonNode.has("state"));
        assertTrue(jsonNode.has("tenantId"));
    }

    @Test
    void loginResultResponseShouldKeepJsonFieldContract() throws Exception {
        LoginResultResponse response = new LoginResultResponse();
        response.setLoginStage("TENANT_SELECT");
        response.setUserId(1L);
        response.setUsername("sunny");
        response.setEmail("sunny@datapillar.com");
        response.setTenants(List.of(new TenantOptionItem(10L, "demo", "Demo Tenant", 1, 1)));

        JsonNode jsonNode = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertTrue(jsonNode.has("loginStage"));
        assertTrue(jsonNode.has("tenants"));
        assertTrue(jsonNode.has("userId"));
        assertTrue(jsonNode.has("username"));
        assertTrue(jsonNode.has("email"));
        assertEquals(10L, jsonNode.get("tenants").get(0).get("tenantId").asLong());
    }
}
