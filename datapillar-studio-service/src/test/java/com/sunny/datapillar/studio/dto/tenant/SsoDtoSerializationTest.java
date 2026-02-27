package com.sunny.datapillar.studio.dto.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sunny.datapillar.studio.dto.tenant.request.SsoConfigCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.response.SsoConfigResponse;
import com.sunny.datapillar.studio.dto.tenant.response.SsoDingtalkConfigItem;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void ssoConfigCreateRequestShouldKeepJsonFieldContract() throws Exception {
        SsoConfigCreateRequest request = new SsoConfigCreateRequest();
        request.setProvider("dingtalk");
        request.setBaseUrl("https://oapi.dingtalk.com");
        request.setStatus(1);
        request.setConfig(buildConfig());

        JsonNode jsonNode = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertTrue(jsonNode.has("provider"));
        assertTrue(jsonNode.has("baseUrl"));
        assertTrue(jsonNode.has("config"));
        assertTrue(jsonNode.has("status"));
        assertTrue(jsonNode.get("config").has("clientId"));
        assertTrue(jsonNode.get("config").has("clientSecret"));
        assertTrue(jsonNode.get("config").has("redirectUri"));
    }

    @Test
    void ssoConfigResponseShouldKeepJsonFieldContract() throws Exception {
        SsoConfigResponse response = new SsoConfigResponse();
        response.setId(100L);
        response.setTenantId(10L);
        response.setProvider("dingtalk");
        response.setBaseUrl("https://oapi.dingtalk.com");
        response.setStatus(1);
        response.setHasClientSecret(true);
        response.setConfig(buildConfig());
        response.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));

        JsonNode jsonNode = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertTrue(jsonNode.has("id"));
        assertTrue(jsonNode.has("tenantId"));
        assertTrue(jsonNode.has("provider"));
        assertTrue(jsonNode.has("baseUrl"));
        assertTrue(jsonNode.has("status"));
        assertTrue(jsonNode.has("hasClientSecret"));
        assertTrue(jsonNode.has("config"));
        assertTrue(jsonNode.has("updatedAt"));
        assertEquals("dingtalk", jsonNode.get("provider").asText());
    }

    private SsoDingtalkConfigItem buildConfig() {
        SsoDingtalkConfigItem config = new SsoDingtalkConfigItem();
        config.setClientId("client-id");
        config.setClientSecret("secret");
        config.setRedirectUri("https://redirect");
        return config;
    }
}
