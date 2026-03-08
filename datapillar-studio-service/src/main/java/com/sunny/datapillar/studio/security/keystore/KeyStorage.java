package com.sunny.datapillar.studio.security.keystore;

/** Tenant private key storage contract. */
public interface KeyStorage {

  void savePrivateKey(String tenantCode, byte[] privateKeyPemBytes);

  byte[] loadPrivateKey(String tenantCode);

  boolean existsPrivateKey(String tenantCode);

  void deletePrivateKey(String tenantCode);
}
