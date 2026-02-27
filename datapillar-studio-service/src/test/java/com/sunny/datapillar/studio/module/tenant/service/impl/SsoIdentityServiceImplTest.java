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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.module.tenant.entity.TenantSsoConfig;
import com.sunny.datapillar.studio.module.tenant.entity.UserIdentity;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserIdentityMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantCodeResolver;
import com.sunny.datapillar.studio.module.tenant.service.sso.provider.DingtalkBindingClient;
import com.sunny.datapillar.studio.module.tenant.service.sso.provider.model.DingtalkUserInfo;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import java.sql.SQLException;
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
    private AuthCryptoRpcClient authCryptoClient;
    @Mock
    private TenantCodeResolver tenantCodeResolver;

    private SsoIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserIdentity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TenantSsoConfig.class);
        TenantContextHolder.set(new TenantContext(1L, "tenant-1", null, null, false));
        service = new SsoIdentityServiceImpl(
                userIdentityMapper,
                tenantSsoConfigMapper,
                tenantUserMapper,
                dingtalkBindingClient,
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
    void bindByCode_shouldCreateIdentityWhenValid() throws Exception {
        SsoIdentityBindByCodeRequest request = new SsoIdentityBindByCodeRequest();
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
        when(authCryptoClient.decryptSsoClientSecret("tenant-1", "ENCv1:secret")).thenReturn("secret");

        DingtalkUserInfo userInfo = new DingtalkUserInfo("union-001", "{\"unionId\":\"union-001\"}");
        when(dingtalkBindingClient.fetchUserInfo("client", "secret", "code-001")).thenReturn(userInfo);
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
        SsoIdentityBindByCodeRequest request = new SsoIdentityBindByCodeRequest();
        request.setUserId(100L);
        request.setProvider("dingtalk");
        request.setAuthCode("code-001");

        when(tenantUserMapper.selectByTenantIdAndUserId(1L, 100L)).thenReturn(null);

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> service.bindByCode(request));
        assertEquals("无SSO绑定权限", exception.getMessage());
    }

    @Test
    void bindByCode_shouldRejectDuplicateBinding() throws Exception {
        SsoIdentityBindByCodeRequest request = new SsoIdentityBindByCodeRequest();
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
        when(authCryptoClient.decryptSsoClientSecret("tenant-1", "ENCv1:secret")).thenReturn("secret");

        DingtalkUserInfo userInfo = new DingtalkUserInfo("union-001", "{\"unionId\":\"union-001\"}");
        when(dingtalkBindingClient.fetchUserInfo("client", "secret", "code-001")).thenReturn(userInfo);
        when(userIdentityMapper.insert(any(UserIdentity.class))).thenThrow(new RuntimeException(
                new SQLException("Duplicate entry union-001 for key uq_user_identity", "23000", 1062)));

        AlreadyExistsException exception = assertThrows(AlreadyExistsException.class, () -> service.bindByCode(request));
        assertEquals("SSO身份已存在", exception.getMessage());
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
