package com.sunny.datapillar.studio.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoGenericClient;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 租户密钥Check组件
 * 负责租户密钥Check核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantKeyCheck implements ApplicationRunner {

    private static final int MAX_ID_LOG_SIZE = 20;

    private final TenantMapper tenantMapper;
    private final AuthCryptoGenericClient authCryptoClient;

    @Override
    public void run(ApplicationArguments args) {
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Tenant::getId);
        List<Tenant> tenants = tenantMapper.selectList(wrapper);
        if (tenants == null || tenants.isEmpty()) {
            log.info("租户密钥完整性检查通过: 无租户数据");
            return;
        }

        List<Long> missingPublicKeyTenantIds = new ArrayList<>();
        List<Long> missingPrivateKeyTenantIds = new ArrayList<>();

        for (Tenant tenant : tenants) {
            Long tenantId = tenant.getId();
            if (tenantId == null || tenantId <= 0) {
                continue;
            }

            if (!StringUtils.hasText(tenant.getEncryptPublicKey())) {
                missingPublicKeyTenantIds.add(tenantId);
            }

            try {
                if (!authCryptoClient.existsPrivateKey(tenantId)) {
                    missingPrivateKeyTenantIds.add(tenantId);
                }
            } catch (RuntimeException ex) {
                throw new IllegalStateException("租户私钥存储检查失败: tenantId=" + tenantId, ex);
            }
        }

        if (missingPublicKeyTenantIds.isEmpty() && missingPrivateKeyTenantIds.isEmpty()) {
            log.info("租户密钥完整性检查通过: tenantCount={}", tenants.size());
            return;
        }

        String message = "租户密钥完整性检查失败: missingPublicKeyTenantIds="
                + formatIds(missingPublicKeyTenantIds)
                + ", missingPrivateKeyTenantIds="
                + formatIds(missingPrivateKeyTenantIds);
        log.error(message);
        throw new IllegalStateException(message);
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
}
