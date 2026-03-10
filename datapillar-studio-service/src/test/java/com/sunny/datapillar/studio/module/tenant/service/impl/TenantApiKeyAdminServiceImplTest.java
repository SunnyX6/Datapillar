package com.sunny.datapillar.studio.module.tenant.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.dto.tenant.request.TenantApiKeyCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.response.TenantApiKeyCreateResponse;
import com.sunny.datapillar.studio.dto.tenant.response.TenantApiKeyItemResponse;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.module.tenant.entity.TenantApiKey;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantApiKeyMapper;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import com.sunny.datapillar.studio.security.apikey.TenantApiKeyGenerator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class TenantApiKeyAdminServiceImplTest {

  @Mock private TenantApiKeyMapper tenantApiKeyMapper;
  @Mock private TenantApiKeyGenerator tenantApiKeyGenerator;

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void createApiKey_shouldPersistAndReturnPlaintextOnce() {
    TenantApiKeyAdminServiceImpl service = createService();
    setRequestContext(1001L, 101L);
    when(tenantApiKeyMapper.selectByTenantIdAndName(1001L, "lineage-ingest")).thenReturn(null);
    when(tenantApiKeyMapper.countUsableByTenantId(eq(1001L), any())).thenReturn(0);
    when(tenantApiKeyGenerator.generate()).thenReturn("dpk_plaintext_key_1234");
    when(tenantApiKeyMapper.insert(any(TenantApiKey.class)))
        .thenAnswer(
            invocation -> {
              TenantApiKey entity = invocation.getArgument(0);
              entity.setId(11L);
              entity.setCreatedAt(LocalDateTime.now());
              return 1;
            });

    TenantApiKeyCreateRequest request = new TenantApiKeyCreateRequest();
    request.setName("lineage-ingest");
    request.setDescription("OpenLineage events");
    request.setExpiresAt(OffsetDateTime.now().plusDays(30));

    TenantApiKeyCreateResponse response = service.createApiKey(request);

    assertEquals(11L, response.getId());
    assertEquals("API_KEY", response.getAuthType());
    assertEquals("/openapi/**", response.getApiDomain());
    assertEquals("Authorization", response.getHeaderName());
    assertEquals("Bearer", response.getHeaderScheme());
    assertEquals("1234", response.getLastFour());
    assertEquals("dpk_plaintext_key_1234", response.getPlainApiKey());
    assertNotNull(response.getUsageExample());
  }

  @Test
  void createApiKey_shouldRejectDuplicateName() {
    TenantApiKeyAdminServiceImpl service = createService();
    setRequestContext(1001L, 101L);
    TenantApiKey existing = new TenantApiKey();
    existing.setId(12L);
    when(tenantApiKeyMapper.selectByTenantIdAndName(1001L, "lineage-ingest")).thenReturn(existing);

    TenantApiKeyCreateRequest request = new TenantApiKeyCreateRequest();
    request.setName("lineage-ingest");

    assertThrows(AlreadyExistsException.class, () -> service.createApiKey(request));
  }

  @Test
  void createApiKey_shouldRejectWhenActiveLimitExceeded() {
    TenantApiKeyAdminServiceImpl service = createService();
    setRequestContext(1001L, 101L);
    when(tenantApiKeyMapper.selectByTenantIdAndName(1001L, "lineage-ingest")).thenReturn(null);
    when(tenantApiKeyMapper.countUsableByTenantId(eq(1001L), any())).thenReturn(10);

    TenantApiKeyCreateRequest request = new TenantApiKeyCreateRequest();
    request.setName("lineage-ingest");

    assertThrows(BadRequestException.class, () -> service.createApiKey(request));
  }

  @Test
  void createApiKey_shouldRejectApiKeyPrincipal() {
    TenantApiKeyAdminServiceImpl service = createService();
    setRequestContext(1001L, null, PrincipalType.API_KEY);

    TenantApiKeyCreateRequest request = new TenantApiKeyCreateRequest();
    request.setName("lineage-ingest");

    assertThrows(UnauthorizedException.class, () -> service.createApiKey(request));
  }

  @Test
  void listApiKeys_shouldReturnMaskedItems() {
    TenantApiKeyAdminServiceImpl service = createService();
    setRequestContext(1001L, 101L);
    TenantApiKey tenantApiKey = new TenantApiKey();
    tenantApiKey.setId(13L);
    tenantApiKey.setTenantId(1001L);
    tenantApiKey.setName("lineage-ingest");
    tenantApiKey.setDescription("OpenLineage events");
    tenantApiKey.setLastFour("1234");
    tenantApiKey.setStatus(1);
    tenantApiKey.setCreatedBy(101L);
    tenantApiKey.setCreatedAt(LocalDateTime.now());
    when(tenantApiKeyMapper.selectByTenantId(1001L)).thenReturn(List.of(tenantApiKey));

    List<TenantApiKeyItemResponse> responses = service.listApiKeys();

    assertEquals(1, responses.size());
    assertEquals("API_KEY", responses.getFirst().getAuthType());
    assertEquals("1234", responses.getFirst().getLastFour());
  }

  @Test
  void disableApiKey_shouldMarkDisabled() {
    TenantApiKeyAdminServiceImpl service = createService();
    setRequestContext(1001L, 101L);
    TenantApiKey tenantApiKey = new TenantApiKey();
    tenantApiKey.setId(14L);
    tenantApiKey.setTenantId(1001L);
    tenantApiKey.setStatus(1);
    when(tenantApiKeyMapper.selectById(14L)).thenReturn(tenantApiKey);

    service.disableApiKey(14L);

    assertEquals(0, tenantApiKey.getStatus());
    assertEquals(101L, tenantApiKey.getDisabledBy());
    assertNotNull(tenantApiKey.getDisabledAt());
    verify(tenantApiKeyMapper).updateById(tenantApiKey);
  }

  private TenantApiKeyAdminServiceImpl createService() {
    return new TenantApiKeyAdminServiceImpl(
        tenantApiKeyMapper, tenantApiKeyGenerator, new StudioDbExceptionTranslator());
  }

  private void setRequestContext(Long tenantId, Long userId) {
    setRequestContext(tenantId, userId, PrincipalType.USER);
  }

  private void setRequestContext(Long tenantId, Long userId, PrincipalType principalType) {
    TenantContextHolder.set(new TenantContext(tenantId, "tenant-a", null, null, false));
    MockHttpServletRequest request = new MockHttpServletRequest();
    TrustedIdentityContext.attach(
        request,
        new TrustedIdentityContext(
            principalType,
            principalType == PrincipalType.API_KEY ? "api-key:201" : "user:" + userId,
            userId,
            tenantId,
            "tenant-a",
            principalType == PrincipalType.API_KEY ? "lineage-ingest" : "sunny",
            principalType == PrincipalType.API_KEY ? null : "sunny@datapillar.ai",
            List.of("ADMIN"),
            false,
            null,
            null,
            null));
    HttpServletRequest servletRequest = request;
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
  }
}
