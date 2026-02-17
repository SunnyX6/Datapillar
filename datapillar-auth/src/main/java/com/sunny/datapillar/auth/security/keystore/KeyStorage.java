package com.sunny.datapillar.auth.security.keystore;

/**
 * 密钥Storage接口
 * 定义密钥Storage能力契约与行为边界
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface KeyStorage {

    void savePrivateKey(Long tenantId, byte[] pemBytes);

    byte[] loadPrivateKey(Long tenantId);

    boolean exists(Long tenantId);
}
