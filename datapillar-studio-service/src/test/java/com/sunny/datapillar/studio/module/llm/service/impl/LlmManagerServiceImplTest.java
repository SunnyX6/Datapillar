package com.sunny.datapillar.studio.module.llm.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.dto.LlmProviderDto;
import com.sunny.datapillar.studio.module.llm.entity.AiModelGrant;
import com.sunny.datapillar.studio.module.llm.entity.AiModel;
import com.sunny.datapillar.studio.module.llm.entity.AiProvider;
import com.sunny.datapillar.studio.module.llm.entity.AiUsage;
import com.sunny.datapillar.studio.module.llm.enums.AiModelStatus;
import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import com.sunny.datapillar.studio.module.llm.mapper.AiModelGrantMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiModelMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiProviderMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiUsageMapper;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantCodeResolver;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmManagerServiceImplTest {

    @Mock
    private AiProviderMapper aiProviderMapper;
    @Mock
    private AiModelMapper aiModelMapper;
    @Mock
    private AiModelGrantMapper aiModelGrantMapper;
    @Mock
    private AiUsageMapper aiUsageMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuthCryptoRpcClient authCryptoClient;
    @Mock
    private TenantCodeResolver tenantCodeResolver;

    private LlmManagerServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiModel.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiProvider.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiModelGrant.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiUsage.class);
        TenantContextHolder.set(new TenantContext(1L, "tenant-1", null, null, false));
        service = Mockito.spy(new LlmManagerServiceImpl(
                aiProviderMapper,
                aiModelMapper,
                aiModelGrantMapper,
                aiUsageMapper,
                permissionMapper,
                userMapper,
                authCryptoClient,
                tenantCodeResolver,
                new ObjectMapper()
        ));
        lenient().when(tenantCodeResolver.requireTenantCode(1L)).thenReturn("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createModel_shouldRejectDuplicateModelId() {
        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        when(aiProviderMapper.selectOne(any())).thenReturn(provider);
        when(aiModelMapper.selectCount(any())).thenReturn(1L);

        LlmManagerDto.CreateRequest request = new LlmManagerDto.CreateRequest();
        request.setModelId("openai/gpt-4o");
        request.setName("GPT-4o");
        request.setProviderCode("openai");
        request.setModelType(AiModelType.CHAT);

        AlreadyExistsException exception = assertThrows(AlreadyExistsException.class,
                () -> service.createModel(100L, request));
        assertEquals("资源已存在", exception.getMessage());
    }

    @Test
    void createModel_shouldEncryptApiKeyAndActivateModel() {
        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        provider.setName("OpenAI");
        provider.setBaseUrl("https://api.openai.com/v1");
        when(aiProviderMapper.selectOne(any())).thenReturn(provider);
        when(aiModelMapper.selectCount(any())).thenReturn(0L);
        when(aiModelMapper.insert(any(AiModel.class))).thenAnswer(invocation -> {
            AiModel inserting = invocation.getArgument(0);
            inserting.setId(10L);
            return 1;
        });
        when(authCryptoClient.encryptLlmApiKey("tenant-1", "sk-test")).thenReturn("ENCv1:encrypted");
        doNothing().when(service).verifyModelConnection(any(), any(), any(), any(), any());

        LlmManagerDto.CreateRequest request = new LlmManagerDto.CreateRequest();
        request.setModelId("openai/gpt-4o");
        request.setName("GPT-4o");
        request.setProviderCode("openai");
        request.setModelType(AiModelType.CHAT);
        request.setApiKey("sk-test");

        LlmManagerDto.ModelResponse response = service.createModel(100L, request);

        assertTrue(Boolean.TRUE.equals(response.getHasApiKey()));

        ArgumentCaptor<AiModel> modelCaptor = ArgumentCaptor.forClass(AiModel.class);
        verify(aiModelMapper).insert(modelCaptor.capture());
        AiModel created = modelCaptor.getValue();
        assertEquals(AiModelStatus.ACTIVE, created.getStatus());
        assertEquals("ENCv1:encrypted", created.getApiKey());
        assertEquals("https://api.openai.com/v1", created.getBaseUrl());

        verify(service).verifyModelConnection("openai", "openai/gpt-4o", AiModelType.CHAT, "sk-test",
                "https://api.openai.com/v1");
    }

    @Test
    void createProvider_shouldNotInsertWhenCodeExists() {
        AiProvider provider = new AiProvider();
        provider.setId(9L);
        provider.setCode("openrouter");
        provider.setName("OpenRouter");
        provider.setModelIds("[\"openrouter/model-a\"]");
        when(aiProviderMapper.selectOne(any())).thenReturn(provider);

        LlmProviderDto.CreateRequest request = new LlmProviderDto.CreateRequest();
        request.setCode("OpenRouter");
        request.setName("OpenRouter");

        service.createProvider(100L, request);

        verify(aiProviderMapper, times(0)).insert(any(AiProvider.class));
    }

    @Test
    void updateProvider_shouldMergeModelIds() {
        AiProvider provider = new AiProvider();
        provider.setId(9L);
        provider.setCode("openrouter");
        provider.setName("Old");
        provider.setBaseUrl("https://old");
        provider.setModelIds("[\"openrouter/model-a\",\"openrouter/model-b\"]");
        when(aiProviderMapper.selectOne(any())).thenReturn(provider);
        when(aiProviderMapper.updateById(any(AiProvider.class))).thenReturn(1);

        LlmProviderDto.UpdateRequest request = new LlmProviderDto.UpdateRequest();
        request.setName("OpenRouter");
        request.setBaseUrl("https://openrouter.ai/api/v1");
        request.setAddModelIds(List.of("openrouter/model-b", "openrouter/model-c"));
        request.setRemoveModelIds(List.of("openrouter/model-a"));

        service.updateProvider(100L, "OPENROUTER", request);

        ArgumentCaptor<AiProvider> providerCaptor = ArgumentCaptor.forClass(AiProvider.class);
        verify(aiProviderMapper).updateById(providerCaptor.capture());
        AiProvider updated = providerCaptor.getValue();
        assertEquals("OpenRouter", updated.getName());
        assertEquals("https://openrouter.ai/api/v1", updated.getBaseUrl());
        assertEquals("[\"openrouter/model-b\",\"openrouter/model-c\"]", updated.getModelIds());
    }

    @Test
    void deleteProvider_shouldDeleteWhenNoModelBound() {
        AiProvider provider = new AiProvider();
        provider.setId(9L);
        provider.setCode("openrouter");
        when(aiProviderMapper.selectOne(any())).thenReturn(provider);
        when(aiModelMapper.selectCount(any())).thenReturn(0L);
        when(aiProviderMapper.deleteById(9L)).thenReturn(1);

        service.deleteProvider(100L, "openrouter");

        verify(aiProviderMapper).deleteById(9L);
    }

    @Test
    void deleteProvider_shouldRejectWhenModelExists() {
        AiProvider provider = new AiProvider();
        provider.setId(9L);
        provider.setCode("openrouter");
        when(aiProviderMapper.selectOne(any())).thenReturn(provider);
        when(aiModelMapper.selectCount(any())).thenReturn(1L);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.deleteProvider(100L, "openrouter"));
        assertEquals("供应商下存在模型，不能删除", exception.getMessage());
        verify(aiProviderMapper, times(0)).deleteById(9L);
    }

    @Test
    void updateProvider_shouldRejectWhenAddAndRemoveIntersect() {
        AiProvider provider = new AiProvider();
        provider.setId(9L);
        provider.setCode("openrouter");
        provider.setName("OpenRouter");
        provider.setModelIds("[\"openrouter/model-a\"]");
        when(aiProviderMapper.selectOne(any())).thenReturn(provider);

        LlmProviderDto.UpdateRequest request = new LlmProviderDto.UpdateRequest();
        request.setAddModelIds(List.of("openrouter/model-a"));
        request.setRemoveModelIds(List.of("openrouter/model-a"));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.updateProvider(100L, "openrouter", request));
        assertEquals("add_model_ids 与 remove_model_ids 不能有重复值", exception.getMessage());
        verify(aiProviderMapper, times(0)).updateById(any(AiProvider.class));
    }

    @Test
    void listModels_shouldNotFilterByCreator() {
        AiModel model = new AiModel();
        model.setId(10L);
        model.setTenantId(1L);
        model.setModelId("openai/gpt-4o");
        model.setName("GPT-4o");
        model.setProviderId(1L);
        model.setModelType(AiModelType.CHAT);

        when(aiModelMapper.selectList(any())).thenReturn(java.util.List.of(model));

        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        provider.setName("OpenAI");
        when(aiProviderMapper.selectByIds(any())).thenReturn(java.util.List.of(provider));

        service.listModels(null, null, null, 101L);

        ArgumentCaptor<LambdaQueryWrapper<AiModel>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(aiModelMapper).selectList(queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getCustomSqlSegment();
        assertNotNull(sqlSegment);
        assertTrue(sqlSegment.contains("tenant_id"));
        assertFalse(sqlSegment.contains("created_by"));
    }

    @Test
    void listModels_shouldReturnMaskedApiKey() {
        AiModel model = new AiModel();
        model.setId(10L);
        model.setTenantId(1L);
        model.setModelId("openai/gpt-4o");
        model.setName("GPT-4o");
        model.setProviderId(1L);
        model.setModelType(AiModelType.CHAT);
        model.setApiKey("ENCv1:encrypted");

        when(aiModelMapper.selectList(any())).thenReturn(List.of(model));

        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        provider.setName("OpenAI");
        when(aiProviderMapper.selectByIds(any())).thenReturn(List.of(provider));
        when(authCryptoClient.decryptLlmApiKey("tenant-1", "ENCv1:encrypted")).thenReturn("sk-1234567890");

        List<LlmManagerDto.ModelResponse> rows = service.listModels(null, null, null, 101L);

        assertEquals(1, rows.size());
        assertEquals("sk-1*****7890", rows.get(0).getMaskedApiKey());
    }

    @Test
    void listModels_shouldReturnFallbackMaskWhenDecryptFailed() {
        AiModel model = new AiModel();
        model.setId(10L);
        model.setTenantId(1L);
        model.setModelId("openai/gpt-4o");
        model.setName("GPT-4o");
        model.setProviderId(1L);
        model.setModelType(AiModelType.CHAT);
        model.setApiKey("ENCv1:encrypted");

        when(aiModelMapper.selectList(any())).thenReturn(List.of(model));

        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        provider.setName("OpenAI");
        when(aiProviderMapper.selectByIds(any())).thenReturn(List.of(provider));
        when(authCryptoClient.decryptLlmApiKey("tenant-1", "ENCv1:encrypted"))
                .thenThrow(new InternalException("decrypt_failed"));

        List<LlmManagerDto.ModelResponse> rows = service.listModels(null, null, null, 101L);

        assertEquals(1, rows.size());
        assertEquals("******", rows.get(0).getMaskedApiKey());
    }

    @Test
    void upsertUserModelGrant_shouldCreateReadGrant() {
        User user = new User();
        user.setId(200L);
        when(userMapper.selectByIdAndTenantId(1L, 200L)).thenReturn(user);
        when(permissionMapper.selectSystemPermissions()).thenReturn(defaultPermissions());

        AiModel model = new AiModel();
        model.setId(10L);
        model.setTenantId(1L);
        model.setModelId("openai/gpt-4o");
        model.setName("GPT-4o");
        model.setProviderId(1L);
        model.setModelType(AiModelType.CHAT);
        model.setStatus(AiModelStatus.ACTIVE);
        when(aiModelMapper.selectOne(any())).thenReturn(model);
        AiModelGrant refreshed = new AiModelGrant();
        refreshed.setId(1L);
        refreshed.setTenantId(1L);
        refreshed.setUserId(200L);
        refreshed.setModelId(10L);
        refreshed.setPermissionId(2L);
        refreshed.setIsDefault(Boolean.FALSE);
        when(aiModelGrantMapper.selectOne(any())).thenReturn(null, refreshed);
        when(aiModelGrantMapper.insert(any(AiModelGrant.class))).thenReturn(1);
        when(aiUsageMapper.selectOne(any())).thenReturn(null);

        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        provider.setName("OpenAI");
        when(aiProviderMapper.selectById(1L)).thenReturn(provider);

        LlmManagerDto.ModelGrantRequest request = new LlmManagerDto.ModelGrantRequest();
        request.setPermissionCode("READ");
        request.setIsDefault(false);
        LlmManagerDto.ModelUsageResponse response = service.upsertUserModelGrant(101L, 200L, 10L, request);

        assertEquals(200L, response.getUserId());
        assertEquals(10L, response.getAiModelId());
        assertEquals("openai", response.getProviderCode());
        assertEquals("READ", response.getPermissionCode());
        assertFalse(Boolean.TRUE.equals(response.getIsDefault()));

        ArgumentCaptor<AiModelGrant> grantCaptor = ArgumentCaptor.forClass(AiModelGrant.class);
        verify(aiModelGrantMapper).insert(grantCaptor.capture());
        AiModelGrant created = grantCaptor.getValue();
        assertEquals(1L, created.getTenantId());
        assertEquals(200L, created.getUserId());
        assertEquals(10L, created.getModelId());
        assertEquals(2L, created.getPermissionId());
    }

    @Test
    void setUserDefaultModel_shouldSwitchDefaultInGrant() {
        User user = new User();
        user.setId(200L);
        when(userMapper.selectByIdAndTenantId(1L, 200L)).thenReturn(user);
        when(permissionMapper.selectSystemPermissions()).thenReturn(defaultPermissions());

        AiModelGrant current = new AiModelGrant();
        current.setId(1L);
        current.setTenantId(1L);
        current.setUserId(200L);
        current.setModelId(10L);
        current.setPermissionId(2L);
        current.setIsDefault(Boolean.FALSE);

        AiModelGrant refreshed = new AiModelGrant();
        refreshed.setId(1L);
        refreshed.setTenantId(1L);
        refreshed.setUserId(200L);
        refreshed.setModelId(10L);
        refreshed.setPermissionId(2L);
        refreshed.setIsDefault(Boolean.TRUE);

        when(aiModelGrantMapper.selectOne(any())).thenReturn(current, refreshed);
        when(aiModelGrantMapper.update(isNull(), any())).thenReturn(1);
        when(aiModelGrantMapper.updateById(any(AiModelGrant.class))).thenReturn(1);
        when(aiUsageMapper.selectOne(any())).thenReturn(null);

        AiModel model = new AiModel();
        model.setId(10L);
        model.setTenantId(1L);
        model.setProviderId(1L);
        model.setModelId("openai/gpt-4o");
        model.setName("GPT-4o");
        model.setModelType(AiModelType.CHAT);
        model.setStatus(AiModelStatus.ACTIVE);
        when(aiModelMapper.selectOne(any())).thenReturn(model);

        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        provider.setName("OpenAI");
        when(aiProviderMapper.selectById(1L)).thenReturn(provider);

        LlmManagerDto.ModelUsageResponse response = service.setUserDefaultModel(200L, 200L, 10L);

        assertTrue(Boolean.TRUE.equals(response.getIsDefault()));
        verify(aiModelGrantMapper, times(1)).update(isNull(), any());
        verify(aiModelGrantMapper, times(1)).updateById(any(AiModelGrant.class));

        ArgumentCaptor<LambdaUpdateWrapper<AiModelGrant>> updateCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(aiModelGrantMapper).update(isNull(), updateCaptor.capture());
        assertTrue(updateCaptor.getValue().getCustomSqlSegment().contains("user_id"));
    }

    @Test
    void connectModel_shouldEncryptKeyAndActivate() {
        AiModel model = new AiModel();
        model.setId(10L);
        model.setTenantId(1L);
        model.setProviderId(1L);
        model.setModelId("openai/gpt-4o");
        model.setModelType(AiModelType.CHAT);
        model.setStatus(AiModelStatus.CONNECT);
        model.setBaseUrl("https://api.openai.com/v1");

        when(aiModelMapper.selectOne(any())).thenReturn(model);
        when(aiModelMapper.updateById(any(AiModel.class))).thenReturn(1);

        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        when(aiProviderMapper.selectById(1L)).thenReturn(provider);

        when(authCryptoClient.encryptLlmApiKey("tenant-1", "sk-test")).thenReturn("ENCv1:encrypted");
        doNothing().when(service).verifyModelConnection(any(), any(), any(), any(), any());

        LlmManagerDto.ConnectRequest request = new LlmManagerDto.ConnectRequest();
        request.setApiKey("sk-test");

        LlmManagerDto.ConnectResponse response = service.connectModel(101L, 10L, request);

        assertTrue(response.isConnected());
        assertTrue(response.isHasApiKey());

        ArgumentCaptor<AiModel> modelCaptor = ArgumentCaptor.forClass(AiModel.class);
        verify(aiModelMapper).updateById(modelCaptor.capture());
        AiModel updated = modelCaptor.getValue();
        assertEquals(AiModelStatus.ACTIVE, updated.getStatus());
        assertEquals("ENCv1:encrypted", updated.getApiKey());

        verify(service).verifyModelConnection("openai", "openai/gpt-4o", AiModelType.CHAT, "sk-test",
                "https://api.openai.com/v1");
    }

    @Test
    void connectModel_shouldRejectWhenAuthCryptoEncryptFailed() {
        AiModel model = new AiModel();
        model.setId(10L);
        model.setTenantId(1L);
        model.setProviderId(1L);
        model.setModelId("openai/gpt-4o");
        model.setModelType(AiModelType.CHAT);
        model.setStatus(AiModelStatus.CONNECT);
        model.setBaseUrl("https://api.openai.com/v1");
        when(aiModelMapper.selectOne(any())).thenReturn(model);

        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        when(aiProviderMapper.selectById(1L)).thenReturn(provider);

        when(authCryptoClient.encryptLlmApiKey("tenant-1", "sk-test"))
                .thenThrow(new InternalException("tenant_public_key_missing"));
        doNothing().when(service).verifyModelConnection(any(), any(), any(), any(), any());

        LlmManagerDto.ConnectRequest request = new LlmManagerDto.ConnectRequest();
        request.setApiKey("sk-test");

        InternalException exception = assertThrows(InternalException.class,
                () -> service.connectModel(101L, 10L, request));
        assertEquals("tenant_public_key_missing", exception.getMessage());
    }

    private List<Permission> defaultPermissions() {
        Permission disable = new Permission();
        disable.setId(1L);
        disable.setCode("DISABLE");
        disable.setLevel(0);

        Permission read = new Permission();
        read.setId(2L);
        read.setCode("READ");
        read.setLevel(1);

        Permission admin = new Permission();
        admin.setId(3L);
        admin.setCode("ADMIN");
        admin.setLevel(2);

        return List.of(disable, read, admin);
    }
}
