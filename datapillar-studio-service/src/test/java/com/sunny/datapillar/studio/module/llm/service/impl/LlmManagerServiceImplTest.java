package com.sunny.datapillar.studio.module.llm.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.entity.AiModel;
import com.sunny.datapillar.studio.module.llm.entity.AiProvider;
import com.sunny.datapillar.studio.module.llm.entity.AiUsage;
import com.sunny.datapillar.studio.module.llm.enums.AiModelStatus;
import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import com.sunny.datapillar.studio.module.llm.mapper.AiModelMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiProviderMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiUsageMapper;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoGenericClient;
import java.math.BigDecimal;
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
    private AiUsageMapper aiUsageMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuthCryptoGenericClient authCryptoClient;

    private LlmManagerServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiModel.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiProvider.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiUsage.class);
        TenantContextHolder.set(new TenantContext(1L, null, null, false));
        service = Mockito.spy(new LlmManagerServiceImpl(
                aiProviderMapper,
                aiModelMapper,
                aiUsageMapper,
                userMapper,
                authCryptoClient,
                new ObjectMapper()
        ));
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

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createModel(100L, request));
        assertEquals(ErrorCode.DUPLICATE_RESOURCE, exception.getErrorCode());
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

        Page<AiModel> modelPage = Page.of(1, 20, 1);
        modelPage.setRecords(java.util.List.of(model));

        when(aiModelMapper.selectPage(any(), any())).thenReturn(modelPage);

        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        provider.setName("OpenAI");
        when(aiProviderMapper.selectByIds(any())).thenReturn(java.util.List.of(provider));

        service.listModels(20, 0, null, null, null, 101L);

        ArgumentCaptor<LambdaQueryWrapper<AiModel>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(aiModelMapper).selectPage(any(), queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getCustomSqlSegment();
        assertNotNull(sqlSegment);
        assertTrue(sqlSegment.contains("tenant_id"));
        assertFalse(sqlSegment.contains("created_by"));
    }

    @Test
    void grantUserModelUsage_shouldCreateEnabledUsage() {
        User user = new User();
        user.setId(200L);
        when(userMapper.selectByIdAndTenantId(1L, 200L)).thenReturn(user);

        AiModel model = new AiModel();
        model.setId(10L);
        model.setTenantId(1L);
        model.setModelId("openai/gpt-4o");
        model.setName("GPT-4o");
        model.setProviderId(1L);
        model.setModelType(AiModelType.CHAT);
        model.setStatus(AiModelStatus.ACTIVE);
        when(aiModelMapper.selectOne(any())).thenReturn(model);
        when(aiUsageMapper.selectOne(any())).thenReturn(null);
        when(aiUsageMapper.insert(any(AiUsage.class))).thenReturn(1);

        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setCode("openai");
        provider.setName("OpenAI");
        when(aiProviderMapper.selectById(1L)).thenReturn(provider);

        LlmManagerDto.ModelUsageResponse response = service.grantUserModelUsage(101L, 200L, 10L);

        assertEquals(200L, response.getUserId());
        assertEquals(10L, response.getAiModelId());
        assertEquals("openai", response.getProviderCode());
        assertEquals(1, response.getStatus());
        assertFalse(Boolean.TRUE.equals(response.getIsDefault()));

        ArgumentCaptor<AiUsage> usageCaptor = ArgumentCaptor.forClass(AiUsage.class);
        verify(aiUsageMapper).insert(usageCaptor.capture());
        AiUsage created = usageCaptor.getValue();
        assertEquals(1L, created.getTenantId());
        assertEquals(200L, created.getUserId());
        assertEquals(10L, created.getModelId());
        assertEquals(1, created.getStatus());
    }

    @Test
    void setUserDefaultModel_shouldSwitchDefaultInUsage() {
        User user = new User();
        user.setId(200L);
        when(userMapper.selectByIdAndTenantId(1L, 200L)).thenReturn(user);

        AiUsage current = new AiUsage();
        current.setId(1L);
        current.setTenantId(1L);
        current.setUserId(200L);
        current.setModelId(10L);
        current.setStatus(1);
        current.setIsDefault(Boolean.FALSE);
        current.setTotalCostUsd(BigDecimal.ZERO);

        AiUsage refreshed = new AiUsage();
        refreshed.setId(1L);
        refreshed.setTenantId(1L);
        refreshed.setUserId(200L);
        refreshed.setModelId(10L);
        refreshed.setStatus(1);
        refreshed.setIsDefault(Boolean.TRUE);
        refreshed.setTotalCostUsd(BigDecimal.ZERO);

        when(aiUsageMapper.selectOne(any())).thenReturn(current, refreshed);
        when(aiUsageMapper.update(isNull(), any())).thenReturn(2, 1);

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
        verify(aiUsageMapper, times(2)).update(isNull(), any());

        ArgumentCaptor<LambdaUpdateWrapper<AiUsage>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(aiUsageMapper, times(2)).update(isNull(), updateCaptor.capture());
        assertTrue(updateCaptor.getAllValues().get(0).getCustomSqlSegment().contains("user_id"));
        assertTrue(updateCaptor.getAllValues().get(1).getCustomSqlSegment().contains("model_id"));
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

        when(authCryptoClient.encryptLlmApiKey(1L, "sk-test")).thenReturn("ENCv1:encrypted");
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

        when(authCryptoClient.encryptLlmApiKey(1L, "sk-test"))
                .thenThrow(new BusinessException(ErrorCode.SSO_CONFIG_INVALID, "tenant_public_key_missing"));
        doNothing().when(service).verifyModelConnection(any(), any(), any(), any(), any());

        LlmManagerDto.ConnectRequest request = new LlmManagerDto.ConnectRequest();
        request.setApiKey("sk-test");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.connectModel(101L, 10L, request));
        assertEquals(ErrorCode.SSO_CONFIG_INVALID, exception.getErrorCode());
    }
}
