package com.sunny.datapillar.studio.rpc.crypto;

import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import java.util.Map;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 认证加解密Generic客户端
 * 负责认证加解密Generic客户端调用与协议封装
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class AuthCryptoGenericClient {

    private static final String AUTH_CRYPTO_INTERFACE = "com.sunny.datapillar.auth.rpc.crypto.AuthCryptoService";
    private static final String PURPOSE_SSO_CLIENT_SECRET = "sso.client_secret";
    private static final String PURPOSE_LLM_API_KEY = "llm.api_key";

    @Value("${spring.application.name:datapillar-studio-service}")
    private String caller;

    @DubboReference(
            interfaceName = AUTH_CRYPTO_INTERFACE,
            version = "${datapillar.rpc.version:1.0.0}",
            group = "${datapillar.rpc.group:datapillar}",
            generic = true
    )
    private GenericService authCryptoService;

    public String encryptSsoClientSecret(Long tenantId, String plaintext) {
        return encrypt(tenantId, PURPOSE_SSO_CLIENT_SECRET, plaintext);
    }

    public String decryptSsoClientSecret(Long tenantId, String ciphertext) {
        return decrypt(tenantId, PURPOSE_SSO_CLIENT_SECRET, ciphertext);
    }

    public String encryptLlmApiKey(Long tenantId, String plaintext) {
        return encrypt(tenantId, PURPOSE_LLM_API_KEY, plaintext);
    }

    public void savePrivateKey(Long tenantId, String privateKeyPem) {
        if (!StringUtils.hasText(privateKeyPem)) {
            throw new BadRequestException("参数错误");
        }
        invokeVoid("savePrivateKey", new String[]{
                        Long.class.getName(),
                        String.class.getName(),
                        Map.class.getName()
                },
                new Object[]{tenantId, privateKeyPem, callerAttrs()});
    }

    public boolean existsPrivateKey(Long tenantId) {
        Object value = invoke("existsPrivateKey", new String[]{
                        Long.class.getName(),
                        Map.class.getName()
                },
                new Object[]{tenantId, callerAttrs()});
        if (!(value instanceof Boolean exists)) {
            throw new InternalException("服务器内部错误");
        }
        return exists;
    }

    private String encrypt(Long tenantId, String purpose, String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            throw new BadRequestException("参数错误");
        }
        Object value = invoke("encrypt", new String[]{
                        Long.class.getName(),
                        String.class.getName(),
                        String.class.getName(),
                        Map.class.getName()
                },
                new Object[]{tenantId, purpose, plaintext, callerAttrs()});
        if (!(value instanceof String encrypted) || !StringUtils.hasText(encrypted)) {
            throw new InternalException("服务器内部错误");
        }
        return encrypted;
    }

    private String decrypt(Long tenantId, String purpose, String ciphertext) {
        if (!StringUtils.hasText(ciphertext)) {
            throw new BadRequestException("参数错误");
        }
        Object value = invoke("decrypt", new String[]{
                        Long.class.getName(),
                        String.class.getName(),
                        String.class.getName(),
                        Map.class.getName()
                },
                new Object[]{tenantId, purpose, ciphertext, callerAttrs()});
        if (!(value instanceof String decrypted) || !StringUtils.hasText(decrypted)) {
            throw new InternalException("服务器内部错误");
        }
        return decrypted;
    }

    private Object invoke(String method, String[] parameterTypes, Object[] args) {
        try {
            return authCryptoService.$invoke(method, parameterTypes, args);
        } catch (DatapillarRuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InternalException(ex, "服务器内部错误");
        }
    }

    private void invokeVoid(String method, String[] parameterTypes, Object[] args) {
        invoke(method, parameterTypes, args);
    }

    private Map<String, String> callerAttrs() {
        return Map.of("caller", caller);
    }
}
