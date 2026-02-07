package com.sunny.datapillar.studio.security.keystore;

/**
 * 私钥存储接口（仅存取，不做加解密）
 */
public interface KeyStorage {

    void savePrivateKey(Long tenantId, byte[] pemBytes);

    byte[] loadPrivateKey(Long tenantId);

    boolean exists(Long tenantId);
}
