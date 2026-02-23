package com.sunny.datapillar.studio.module.tenant.service.sso.impl;

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
import com.sunny.datapillar.studio.module.tenant.dto.SsoConfigDto;
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
        service = new SsoConfigServiceImpl(tenantSsoConfigMapper, authCryptoClient, tenantCodeResolver, new ObjectMapper());
        lenient().when(tenantCodeResolver.requireTenantCode(1L)).thenReturn("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createConfig_shouldRejectInvalidProvider() {
        SsoConfigDto.Create dto = new SsoConfigDto.Create();
        dto.setProvider("wecom");
        dto.setStatus(1);
        dto.setConfig(buildConfig("client", "secret", "https://redirect"));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.createConfig(dto));
        assertEquals("参数错误", exception.getMessage());
    }

    @Test
    void createConfig_shouldRejectMissingRequiredFields() {
        SsoConfigDto.Create dto = new SsoConfigDto.Create();
        dto.setProvider("dingtalk");
        dto.setStatus(1);
        dto.setConfig(buildConfig("client", null, "https://redirect"));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.createConfig(dto));
        assertEquals("参数错误", exception.getMessage());
    }

    @Test
    void createConfig_shouldRejectInvalidStatus() {
        SsoConfigDto.Create dto = new SsoConfigDto.Create();
        dto.setProvider("dingtalk");
        dto.setStatus(2);
        dto.setConfig(buildConfig("client", "secret", "https://redirect"));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.createConfig(dto));
        assertEquals("参数错误", exception.getMessage());
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

        List<SsoConfigDto.Response> responses = service.listConfigs();

        assertEquals(1, responses.size());
        SsoConfigDto.Response response = responses.get(0);
        assertTrue(Boolean.TRUE.equals(response.getHasClientSecret()));
        assertNotNull(response.getConfig());
        assertNull(response.getConfig().getClientSecret());
    }

    private SsoConfigDto.DingtalkConfig buildConfig(String clientId, String clientSecret, String redirectUri) {
        SsoConfigDto.DingtalkConfig config = new SsoConfigDto.DingtalkConfig();
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setRedirectUri(redirectUri);
        return config;
    }

}
