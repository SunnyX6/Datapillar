package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.dto.tenant.request.TenantApiKeyCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.response.TenantApiKeyCreateResponse;
import com.sunny.datapillar.studio.dto.tenant.response.TenantApiKeyItemResponse;
import java.util.List;

/** Tenant API key management service. */
public interface TenantApiKeyAdminService {

  List<TenantApiKeyItemResponse> listApiKeys();

  TenantApiKeyCreateResponse createApiKey(TenantApiKeyCreateRequest request);

  void disableApiKey(Long apiKeyId);
}
