package com.sunny.datapillar.studio.module.tenant.service.sso.impl;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.module.tenant.entity.TenantSsoConfig;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantCodeResolver;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SsoConfigServiceImplTest {

    @Mock
    private TenantSsoConfigMapper tenantSsoConfigMapper;
    @Mock
    private AuthCryptoRpcClient authCryptoClient;
    @Mock
    private TenantCodeResolver tenantCodeResolver;

    private SsoConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TenantSsoConfig.class);
        TenantContextHolder.set(new TenantContext(1L, "tenant-1", null, null, false));
        service = new SsoConfigServiceImpl(
                tenantSsoConfigMapper,
                authCryptoClient,
                tenantCodeResolver,
                new ObjectMapper(),
                new StudioDbExceptionTranslator()
        );
        lenient().when(tenantCodeResolver.requireTenantCode(1L)).thenReturn("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createConfig_shouldRejectInvalidProvider() {
        SsoConfigCreateRequest dto = new SsoConfigCreateRequest();
        dto.setProvider("wecom");
        dto.setStatus(1);
        dto.setConfig(buildConfig("client", "secret", "https://redirect"));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.createConfig(dto));
        assertEquals("不支持的SSO供应商", exception.getMessage());
    }

    @Test
    void createConfig_shouldRejectMissingRequiredFields() {
        SsoConfigCreateRequest dto = new SsoConfigCreateRequest();
        dto.setProvider("dingtalk");
        dto.setStatus(1);
        dto.setConfig(buildConfig("client", null, "https://redirect"));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.createConfig(dto));
        assertEquals("SSO配置参数错误", exception.getMessage());
    }

    @Test
    void createConfig_shouldRejectInvalidStatus() {
        SsoConfigCreateRequest dto = new SsoConfigCreateRequest();
        dto.setProvider("dingtalk");
        dto.setStatus(2);
        dto.setConfig(buildConfig("client", "secret", "https://redirect"));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.createConfig(dto));
        assertEquals("SSO配置参数错误", exception.getMessage());
    }

    @Test
    void listConfigs_shouldMaskClientSecret() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String configJson = mapper.writeValueAsString(java.util.Map.of(
                "clientId", "client",
                "clientSecret", "ENCv1:secret",
                "redirectUri", "https://redirect"
        ));
        TenantSsoConfig config = new TenantSsoConfig();
        config.setId(10L);
        config.setTenantId(1L);
        config.setProvider("dingtalk");
        config.setStatus(1);
        config.setConfigJson(configJson);
        config.setUpdatedAt(LocalDateTime.now());

        when(tenantSsoConfigMapper.selectList(any())).thenReturn(List.of(config));

        List<SsoConfigResponse> responses = service.listConfigs();

        assertEquals(1, responses.size());
        SsoConfigResponse response = responses.get(0);
        assertTrue(Boolean.TRUE.equals(response.getHasClientSecret()));
        assertNotNull(response.getConfig());
        assertNull(response.getConfig().getClientSecret());
    }

    private SsoDingtalkConfigItem buildConfig(String clientId, String clientSecret, String redirectUri) {
        SsoDingtalkConfigItem config = new SsoDingtalkConfigItem();
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setRedirectUri(redirectUri);
        return config;
    }

}
