package com.sunny.datapillar.auth.rpc.crypto;

import java.util.Map;

/**
 * 认证加解密服务
 * 提供认证加解密业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface AuthCryptoService {

    String encrypt(Long tenantId, String purpose, String plaintext, Map<String, String> attrs);

    String decrypt(Long tenantId, String purpose, String ciphertext, Map<String, String> attrs);

    void savePrivateKey(Long tenantId, String privateKeyPem, Map<String, String> attrs);

    boolean existsPrivateKey(Long tenantId, Map<String, String> attrs);
}
