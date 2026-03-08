package com.sunny.datapillar.studio.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.crypto.RsaKeyPairGenerator;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.security.KeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalCryptoServiceTest {

  @Mock private TenantKeyService tenantKeyService;

  private LocalCryptoService localCryptoService;

  @BeforeEach
  void setUp() {
    localCryptoService = new LocalCryptoService(tenantKeyService);
  }

  @Test
  void encryptAndDecryptLlmApiKey_shouldRoundTrip() {
    KeyPair keyPair = RsaKeyPairGenerator.generateRsaKeyPair();
    String publicKeyPem = RsaKeyPairGenerator.toPublicKeyPem(keyPair.getPublic());
    byte[] privateKeyPem = RsaKeyPairGenerator.toPrivateKeyPem(keyPair.getPrivate());

    when(tenantKeyService.loadPublicKey("tenant-acme")).thenReturn(publicKeyPem);
    when(tenantKeyService.loadPrivateKey("tenant-acme")).thenReturn(privateKeyPem);

    String ciphertext = localCryptoService.encryptLlmApiKey("tenant-acme", "sk-test-key");

    assertTrue(ciphertext.startsWith("ENCv1:"));
    String plaintext = localCryptoService.decryptLlmApiKey("tenant-acme", ciphertext);
    assertEquals("sk-test-key", plaintext);
  }

  @Test
  void decryptLlmApiKey_shouldRejectInvalidCiphertext() {
    KeyPair keyPair = RsaKeyPairGenerator.generateRsaKeyPair();
    byte[] privateKeyPem = RsaKeyPairGenerator.toPrivateKeyPem(keyPair.getPrivate());
    when(tenantKeyService.loadPrivateKey("tenant-acme")).thenReturn(privateKeyPem);

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> localCryptoService.decryptLlmApiKey("tenant-acme", "plain-value"));

    assertEquals("Parameter error", exception.getMessage());
  }

  @Test
  void generateTenantKey_shouldMapSnapshot() {
    when(tenantKeyService.generateTenantKey("tenant-acme"))
        .thenReturn(
            new TenantKeyService.TenantKeySnapshot(
                "tenant-acme", "READY", "v1", "fp-1", "PUBLIC_KEY"));

    LocalCryptoService.TenantKeySnapshot snapshot =
        localCryptoService.generateTenantKey("tenant-acme");

    assertEquals("tenant-acme", snapshot.tenantCode());
    assertEquals("PUBLIC_KEY", snapshot.publicKeyPem());
    assertEquals("v1", snapshot.keyVersion());
    assertEquals("fp-1", snapshot.fingerprint());
  }

  @Test
  void loadTenantKey_shouldMapSnapshot() {
    when(tenantKeyService.loadTenantKey("tenant-acme"))
        .thenReturn(
            new TenantKeyService.TenantKeySnapshot(
                "tenant-acme", "READY", "v1", "fp-2", "PUBLIC_KEY_2"));

    LocalCryptoService.TenantKeySnapshot snapshot = localCryptoService.loadTenantKey("tenant-acme");

    assertEquals("tenant-acme", snapshot.tenantCode());
    assertEquals("PUBLIC_KEY_2", snapshot.publicKeyPem());
    assertEquals("v1", snapshot.keyVersion());
    assertEquals("fp-2", snapshot.fingerprint());
  }

  @Test
  void deleteTenantKey_shouldDelegateToTenantKeyService() {
    localCryptoService.deleteTenantKey("tenant-acme");
    verify(tenantKeyService).deleteTenantKey("tenant-acme");
  }
}
