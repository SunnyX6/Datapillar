package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.db.DbStorageException;
import com.sunny.datapillar.common.exception.db.SQLExceptionUtils;
import com.sunny.datapillar.common.security.ApiKeyHashSupport;
import com.sunny.datapillar.common.security.AuthType;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.dto.tenant.request.TenantApiKeyCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.response.TenantApiKeyCreateResponse;
import com.sunny.datapillar.studio.dto.tenant.response.TenantApiKeyItemResponse;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.exception.translator.StudioDbScene;
import com.sunny.datapillar.studio.module.tenant.entity.TenantApiKey;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantApiKeyMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantApiKeyAdminService;
import com.sunny.datapillar.studio.security.apikey.TenantApiKeyGenerator;
import com.sunny.datapillar.studio.util.UserContextUtil;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Tenant API key management service implementation. */
@Service
@RequiredArgsConstructor
public class TenantApiKeyAdminServiceImpl implements TenantApiKeyAdminService {

  private static final int STATUS_ACTIVE = 1;
  private static final int STATUS_DISABLED = 0;
  private static final int MAX_ACTIVE_KEYS = 10;
  private static final String API_DOMAIN = "/openapi/**";
  private static final String HEADER_NAME = "Authorization";
  private static final String HEADER_SCHEME = "Bearer";

  private final TenantApiKeyMapper tenantApiKeyMapper;
  private final TenantApiKeyGenerator tenantApiKeyGenerator;
  private final StudioDbExceptionTranslator studioDbExceptionTranslator;

  @Override
  public List<TenantApiKeyItemResponse> listApiKeys() {
    Long tenantId = getRequiredTenantId();
    return tenantApiKeyMapper.selectByTenantId(tenantId).stream()
        .map(this::toItemResponse)
        .toList();
  }

  @Override
  @Transactional
  public TenantApiKeyCreateResponse createApiKey(TenantApiKeyCreateRequest request) {
    Long tenantId = getRequiredTenantId();
    Long creatorUserId = UserContextUtil.getRequiredUserId();
    String name = normalizeRequiredName(request == null ? null : request.getName());
    String description = normalizeDescription(request == null ? null : request.getDescription());
    LocalDateTime expiresAt = toLocalDateTime(request == null ? null : request.getExpiresAt());

    if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
      throw new BadRequestException("expiresAt must be in the future");
    }
    if (tenantApiKeyMapper.selectByTenantIdAndName(tenantId, name) != null) {
      throw new AlreadyExistsException("API key name already exists");
    }
    Integer activeCount = tenantApiKeyMapper.countUsableByTenantId(tenantId, LocalDateTime.now());
    if (activeCount != null && activeCount >= MAX_ACTIVE_KEYS) {
      throw new BadRequestException("Active API key limit exceeded");
    }

    String plainApiKey = tenantApiKeyGenerator.generate();
    TenantApiKey tenantApiKey = new TenantApiKey();
    tenantApiKey.setTenantId(tenantId);
    tenantApiKey.setName(name);
    tenantApiKey.setDescription(description);
    tenantApiKey.setKeyHash(ApiKeyHashSupport.sha256(plainApiKey));
    tenantApiKey.setLastFour(ApiKeyHashSupport.lastFour(plainApiKey));
    tenantApiKey.setStatus(STATUS_ACTIVE);
    tenantApiKey.setExpiresAt(expiresAt);
    tenantApiKey.setCreatedBy(creatorUserId);

    try {
      tenantApiKeyMapper.insert(tenantApiKey);
    } catch (RuntimeException exception) {
      throw translateDbException(exception, StudioDbScene.STUDIO_GENERIC);
    }

    TenantApiKeyCreateResponse response = new TenantApiKeyCreateResponse();
    response.setId(tenantApiKey.getId());
    response.setName(tenantApiKey.getName());
    response.setDescription(tenantApiKey.getDescription());
    response.setAuthType(AuthType.API_KEY.name());
    response.setApiDomain(API_DOMAIN);
    response.setHeaderName(HEADER_NAME);
    response.setHeaderScheme(HEADER_SCHEME);
    response.setLastFour(tenantApiKey.getLastFour());
    response.setStatus(tenantApiKey.getStatus());
    response.setExpiresAt(toOffsetDateTime(tenantApiKey.getExpiresAt()));
    response.setCreatedAt(toOffsetDateTime(tenantApiKey.getCreatedAt()));
    response.setPlainApiKey(plainApiKey);
    response.setUsageExample(
        "curl -H \"Authorization: Bearer "
            + plainApiKey
            + "\" https://<gateway-host>/openapi/<service>/<path>");
    return response;
  }

  @Override
  @Transactional
  public void disableApiKey(Long apiKeyId) {
    Long tenantId = getRequiredTenantId();
    Long operatorUserId = UserContextUtil.getRequiredUserId();
    if (apiKeyId == null || apiKeyId <= 0) {
      throw new BadRequestException("Parameter error");
    }

    TenantApiKey tenantApiKey = tenantApiKeyMapper.selectById(apiKeyId);
    if (tenantApiKey == null || !tenantId.equals(tenantApiKey.getTenantId())) {
      throw new NotFoundException("API key does not exist: %s", apiKeyId);
    }
    if (tenantApiKey.getStatus() != null && tenantApiKey.getStatus() == STATUS_DISABLED) {
      return;
    }

    tenantApiKey.setStatus(STATUS_DISABLED);
    tenantApiKey.setDisabledBy(operatorUserId);
    tenantApiKey.setDisabledAt(LocalDateTime.now());
    tenantApiKeyMapper.updateById(tenantApiKey);
  }

  private TenantApiKeyItemResponse toItemResponse(TenantApiKey tenantApiKey) {
    TenantApiKeyItemResponse response = new TenantApiKeyItemResponse();
    response.setId(tenantApiKey.getId());
    response.setName(tenantApiKey.getName());
    response.setDescription(tenantApiKey.getDescription());
    response.setAuthType(AuthType.API_KEY.name());
    response.setApiDomain(API_DOMAIN);
    response.setHeaderName(HEADER_NAME);
    response.setHeaderScheme(HEADER_SCHEME);
    response.setLastFour(tenantApiKey.getLastFour());
    response.setStatus(tenantApiKey.getStatus());
    response.setExpiresAt(toOffsetDateTime(tenantApiKey.getExpiresAt()));
    response.setLastUsedAt(toOffsetDateTime(tenantApiKey.getLastUsedAt()));
    response.setLastUsedIp(tenantApiKey.getLastUsedIp());
    response.setCreatedBy(tenantApiKey.getCreatedBy());
    response.setDisabledBy(tenantApiKey.getDisabledBy());
    response.setDisabledAt(toOffsetDateTime(tenantApiKey.getDisabledAt()));
    response.setCreatedAt(toOffsetDateTime(tenantApiKey.getCreatedAt()));
    response.setUpdatedAt(toOffsetDateTime(tenantApiKey.getUpdatedAt()));
    return response;
  }

  private Long getRequiredTenantId() {
    TenantContext tenantContext = TenantContextHolder.get();
    if (tenantContext == null
        || tenantContext.getTenantId() == null
        || tenantContext.getTenantId() <= 0) {
      throw new BadRequestException("Tenant context is missing");
    }
    return tenantContext.getTenantId();
  }

  private String normalizeRequiredName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new BadRequestException("name must not be blank");
    }
    return name.trim();
  }

  private String normalizeDescription(String description) {
    if (!StringUtils.hasText(description)) {
      return null;
    }
    return description.trim();
  }

  private LocalDateTime toLocalDateTime(OffsetDateTime offsetDateTime) {
    return offsetDateTime == null ? null : offsetDateTime.toLocalDateTime();
  }

  private OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
    return localDateTime == null
        ? null
        : localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
  }

  private RuntimeException translateDbException(
      RuntimeException runtimeException, StudioDbScene scene) {
    DbStorageException dbException = SQLExceptionUtils.translate(runtimeException);
    if (dbException == null) {
      return runtimeException;
    }
    return studioDbExceptionTranslator.map(scene, dbException);
  }
}
