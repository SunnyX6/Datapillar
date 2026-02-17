package com.sunny.datapillar.auth.rpc.crypto;

import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.security.keystore.KeyStorage;
import com.sunny.datapillar.common.crypto.SecretCodec;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * Auth 统一加解密 RPC 服务实现。
 */
@DubboService(
        interfaceClass = AuthCryptoService.class,
        group = "${datapillar.rpc.group:datapillar}",
        version = "${datapillar.rpc.version:1.0.0}"
)
/**
 * 认证加解密服务实现
 * 实现认证加解密业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@RequiredArgsConstructor
public class AuthCryptoServiceImpl implements AuthCryptoService {

    private final TenantMapper tenantMapper;
    private final KeyStorage keyStorage;

    @Override
    public String encrypt(Long tenantId, String purpose, String plaintext, Map<String, String> attrs) {
        validateTenantId(tenantId);
        validatePurpose(purpose);
        if (!StringUtils.hasText(plaintext)) {
            throw new BadRequestException("参数错误");
        }

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || !StringUtils.hasText(tenant.getEncryptPublicKey())) {
            throw new InternalException("SSO配置无效: %s", "tenant_public_key_missing");
        }

        try {
            return SecretCodec.encrypt(tenant.getEncryptPublicKey(), plaintext);
        } catch (IllegalArgumentException ex) {
            throw new InternalException(ex, "SSO配置无效: %s");
        }
    }

    @Override
    public String decrypt(Long tenantId, String purpose, String ciphertext, Map<String, String> attrs) {
        validateTenantId(tenantId);
        validatePurpose(purpose);
        if (!StringUtils.hasText(ciphertext)) {
            throw new BadRequestException("参数错误");
        }

        try {
            byte[] privateKey = keyStorage.loadPrivateKey(tenantId);
            return SecretCodec.decrypt(privateKey, ciphertext);
        } catch (DatapillarRuntimeException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new InternalException(ex, "SSO配置无效: %s");
        } catch (Exception ex) {
            throw new InternalException(ex, "SSO配置无效: %s", "tenant_private_key_missing");
        }
    }

    @Override
    public void savePrivateKey(Long tenantId, String privateKeyPem, Map<String, String> attrs) {
        validateTenantId(tenantId);
        if (!StringUtils.hasText(privateKeyPem)) {
            throw new BadRequestException("参数错误");
        }
        try {
            keyStorage.savePrivateKey(tenantId, privateKeyPem.getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException ex) {
            throw new InternalException(ex, "服务器内部错误");
        }
    }

    @Override
    public boolean existsPrivateKey(Long tenantId, Map<String, String> attrs) {
        validateTenantId(tenantId);
        try {
            return keyStorage.exists(tenantId);
        } catch (RuntimeException ex) {
            throw new InternalException(ex, "服务器内部错误");
        }
    }

    private void validateTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BadRequestException("参数错误");
        }
    }

    private void validatePurpose(String purpose) {
        if (!StringUtils.hasText(purpose)) {
            throw new BadRequestException("参数错误");
        }
    }
}
