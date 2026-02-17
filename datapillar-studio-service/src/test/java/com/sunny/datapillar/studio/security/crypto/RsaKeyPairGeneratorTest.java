package com.sunny.datapillar.studio.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.sunny.datapillar.common.crypto.RsaKeyPairGenerator;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;

class RsaKeyPairGeneratorTest {

    @Test
    void shouldGenerateKeyPairAndPem() {
        KeyPair keyPair = RsaKeyPairGenerator.generateRsaKeyPair();
        assertThat(keyPair).isNotNull();
        String publicPem = RsaKeyPairGenerator.toPublicKeyPem(keyPair.getPublic());
        byte[] privatePem = RsaKeyPairGenerator.toPrivateKeyPem(keyPair.getPrivate());
        assertThat(publicPem).contains("BEGIN PUBLIC KEY");
        assertThat(new String(privatePem, StandardCharsets.US_ASCII)).contains("BEGIN PRIVATE KEY");
    }
}
