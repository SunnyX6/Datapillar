package com.sunny.datapillar.studio.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Tenant key check during application startup. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantKeyCheck implements ApplicationRunner {

  private static final int MAX_ID_LOG_SIZE = 20;
  private static final int STATUS_ACTIVE = 1;
  private static final String LEGACY_PENDING_PUBLIC_KEY_PLACEHOLDER = "PENDING_PUBLIC_KEY";

  private final TenantMapper tenantMapper;
  private final AuthCryptoRpcClient authCryptoClient;

  @Override
  public void run(ApplicationArguments args) {
    LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByAsc(Tenant::getId);
    List<Tenant> tenants = tenantMapper.selectList(wrapper);
    if (tenants == null || tenants.isEmpty()) {
      log.info("Tenant key integrity check passed: no tenant data");
      return;
    }

    List<Long> missingTenantCodeIds = new ArrayList<>();
    List<Long> missingTenantPublicKeyIds = new ArrayList<>();
    List<Long> missingOrInvalidKeyTenantIds = new ArrayList<>();

    for (Tenant tenant : tenants) {
      if (!isActiveTenant(tenant)) {
        continue;
      }
      Long tenantId = tenant.getId();
      if (tenantId == null || tenantId <= 0) {
        continue;
      }

      String tenantCode = tenant.getCode();
      if (!StringUtils.hasText(tenantCode)) {
        missingTenantCodeIds.add(tenantId);
        continue;
      }
      if (!hasUsablePublicKey(tenant.getEncryptPublicKey())) {
        missingTenantPublicKeyIds.add(tenantId);
      }

      try {
        AuthCryptoRpcClient.TenantKeyStatus keyStatus =
            authCryptoClient.getTenantKeyStatus(tenantCode.trim());
        if (!keyStatus.exists() || !"READY".equalsIgnoreCase(keyStatus.status())) {
          missingOrInvalidKeyTenantIds.add(tenantId);
        }
      } catch (RuntimeException ex) {
        throw new IllegalStateException(
            "Tenant private key storage check failed: tenantId="
                + tenantId
                + ", tenantCode="
                + tenantCode,
            ex);
      }
    }

    if (missingTenantCodeIds.isEmpty()
        && missingTenantPublicKeyIds.isEmpty()
        && missingOrInvalidKeyTenantIds.isEmpty()) {
      log.info("Tenant key integrity check passed: tenantCount={}", tenants.size());
      return;
    }

    String message =
        "Tenant key integrity check failed: missingTenantCodeTenantIds="
            + formatIds(missingTenantCodeIds)
            + ", missingTenantPublicKeyTenantIds="
            + formatIds(missingTenantPublicKeyIds)
            + ", missingOrInvalidKeyTenantIds="
            + formatIds(missingOrInvalidKeyTenantIds);
    log.error(message);
    throw new IllegalStateException(message);
  }

  private boolean isActiveTenant(Tenant tenant) {
    return tenant != null && tenant.getStatus() != null && tenant.getStatus() == STATUS_ACTIVE;
  }

  private String formatIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return "[]";
    }
    if (ids.size() <= MAX_ID_LOG_SIZE) {
      return ids.toString();
    }
    return ids.subList(0, MAX_ID_LOG_SIZE) + "... total=" + ids.size();
  }

  private boolean hasUsablePublicKey(String publicKeyPem) {
    if (!StringUtils.hasText(publicKeyPem)) {
      return false;
    }
    return !LEGACY_PENDING_PUBLIC_KEY_PLACEHOLDER.equals(publicKeyPem.trim());
  }
}
