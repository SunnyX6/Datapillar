package com.sunny.datapillar.studio.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.sunny.datapillar.common.utils.KeyCryptoUtil;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;

class KeyCryptoUtilTest {

    @Test
    void shouldGenerateKeyPairAndPem() {
        KeyPair keyPair = KeyCryptoUtil.generateRsaKeyPair();
        assertThat(keyPair).isNotNull();
        String publicPem = KeyCryptoUtil.toPublicKeyPem(keyPair.getPublic());
        byte[] privatePem = KeyCryptoUtil.toPrivateKeyPem(keyPair.getPrivate());
        assertThat(publicPem).contains("BEGIN PUBLIC KEY");
        assertThat(new String(privatePem, StandardCharsets.US_ASCII)).contains("BEGIN PRIVATE KEY");
    }
}
