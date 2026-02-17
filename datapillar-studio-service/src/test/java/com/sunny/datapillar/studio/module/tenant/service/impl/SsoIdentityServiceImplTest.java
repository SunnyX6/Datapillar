package com.sunny.datapillar.studio.module.tenant.service.sso.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.dto.SsoIdentityDto;
import com.sunny.datapillar.studio.module.tenant.entity.TenantSsoConfig;
import com.sunny.datapillar.studio.module.tenant.entity.UserIdentity;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserIdentityMapper;
import com.sunny.datapillar.studio.module.tenant.service.sso.provider.DingtalkBindingClient;
import com.sunny.datapillar.studio.module.tenant.service.sso.provider.model.DingtalkUserInfo;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoGenericClient;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SsoIdentityServiceImplTest {

    @Mock
    private UserIdentityMapper userIdentityMapper;
    @Mock
    private TenantSsoConfigMapper tenantSsoConfigMapper;
    @Mock
    private TenantUserMapper tenantUserMapper;
    @Mock
    private DingtalkBindingClient dingtalkBindingClient;
    @Mock
    private AuthCryptoGenericClient authCryptoClient;

    private SsoIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserIdentity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TenantSsoConfig.class);
        TenantContextHolder.set(new TenantContext(1L, null, null, false));
        service = new SsoIdentityServiceImpl(
                userIdentityMapper,
                tenantSsoConfigMapper,
                tenantUserMapper,
                dingtalkBindingClient,
                authCryptoClient,
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void bindByCode_shouldCreateIdentityWhenValid() throws Exception {
        SsoIdentityDto.BindByCodeRequest request = new SsoIdentityDto.BindByCodeRequest();
        request.setUserId(100L);
        request.setProvider("dingtalk");
        request.setAuthCode("code-001");

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(1L);
        tenantUser.setUserId(100L);
        tenantUser.setStatus(1);
        when(tenantUserMapper.selectByTenantIdAndUserId(1L, 100L)).thenReturn(tenantUser);

        TenantSsoConfig config = new TenantSsoConfig();
        config.setTenantId(1L);
        config.setProvider("dingtalk");
        config.setStatus(1);
        config.setConfigJson(new ObjectMapper().writeValueAsString(Map.of(
                "clientId", "client",
                "clientSecret", "ENCv1:secret",
                "redirectUri", "https://redirect"
        )));
        when(tenantSsoConfigMapper.selectOne(any())).thenReturn(config);
        when(authCryptoClient.decryptSsoClientSecret(1L, "ENCv1:secret")).thenReturn("secret");

        DingtalkUserInfo userInfo = new DingtalkUserInfo("union-001", "{\"unionId\":\"union-001\"}");
        when(dingtalkBindingClient.fetchUserInfo("client", "secret", "code-001")).thenReturn(userInfo);
        when(userIdentityMapper.selectOne(any())).thenReturn(null);
        when(userIdentityMapper.insert(any(UserIdentity.class))).thenAnswer(invocation -> {
            UserIdentity identity = invocation.getArgument(0);
            identity.setId(10L);
            return 1;
        });

        Long identityId = service.bindByCode(request);

        assertEquals(10L, identityId);
    }

    @Test
    void bindByCode_shouldRejectNonMember() {
        SsoIdentityDto.BindByCodeRequest request = new SsoIdentityDto.BindByCodeRequest();
        request.setUserId(100L);
        request.setProvider("dingtalk");
        request.setAuthCode("code-001");

        when(tenantUserMapper.selectByTenantIdAndUserId(1L, 100L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.bindByCode(request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void bindByCode_shouldRejectDuplicateBinding() throws Exception {
        SsoIdentityDto.BindByCodeRequest request = new SsoIdentityDto.BindByCodeRequest();
        request.setUserId(100L);
        request.setProvider("dingtalk");
        request.setAuthCode("code-001");

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(1L);
        tenantUser.setUserId(100L);
        tenantUser.setStatus(1);
        when(tenantUserMapper.selectByTenantIdAndUserId(1L, 100L)).thenReturn(tenantUser);

        TenantSsoConfig config = new TenantSsoConfig();
        config.setTenantId(1L);
        config.setProvider("dingtalk");
        config.setStatus(1);
        config.setConfigJson(new ObjectMapper().writeValueAsString(Map.of(
                "clientId", "client",
                "clientSecret", "ENCv1:secret",
                "redirectUri", "https://redirect"
        )));
        when(tenantSsoConfigMapper.selectOne(any())).thenReturn(config);
        when(authCryptoClient.decryptSsoClientSecret(1L, "ENCv1:secret")).thenReturn("secret");

        DingtalkUserInfo userInfo = new DingtalkUserInfo("union-001", "{\"unionId\":\"union-001\"}");
        when(dingtalkBindingClient.fetchUserInfo("client", "secret", "code-001")).thenReturn(userInfo);
        when(userIdentityMapper.selectOne(any())).thenReturn(new UserIdentity());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.bindByCode(request));

        assertEquals(ErrorCode.DUPLICATE_RESOURCE, exception.getErrorCode());
    }

    @Test
    void unbind_shouldRemoveIdentity() {
        UserIdentity identity = new UserIdentity();
        identity.setId(10L);
        identity.setTenantId(1L);
        when(userIdentityMapper.selectById(10L)).thenReturn(identity);
        when(userIdentityMapper.deleteById(10L)).thenReturn(1);

        service.unbind(10L);
    }
}
