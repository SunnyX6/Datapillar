package com.sunny.datapillar.openlineage.web.security.keystore;

/** Tenant private key storage contract. */
public interface KeyStorage {

  byte[] loadPrivateKey(String tenantCode);

  boolean existsPrivateKey(String tenantCode);
}
