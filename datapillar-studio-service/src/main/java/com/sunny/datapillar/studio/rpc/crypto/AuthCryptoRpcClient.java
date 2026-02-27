package com.sunny.datapillar.studio.rpc.crypto;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ConflictException;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.rpc.security.v1.CryptoService;
import com.sunny.datapillar.common.rpc.security.v1.DecryptRequest;
import com.sunny.datapillar.common.rpc.security.v1.DecryptResult;
import com.sunny.datapillar.common.rpc.security.v1.EncryptRequest;
import com.sunny.datapillar.common.rpc.security.v1.EncryptResult;
import com.sunny.datapillar.common.rpc.security.v1.EnsureTenantKeyRequest;
import com.sunny.datapillar.common.rpc.security.v1.EnsureTenantKeyResult;
import com.sunny.datapillar.common.rpc.security.v1.GetTenantKeyStatusRequest;
import com.sunny.datapillar.common.rpc.security.v1.GetTenantKeyStatusResult;
import com.sunny.datapillar.common.rpc.security.v1.RpcMeta;
import java.util.Map;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 认证加解密RPC客户端
 * 负责调用统一加解密协议服务
 *
 * @author Sunny
 * @date 2026-02-19
 */
@Component
public class AuthCryptoRpcClient {

    private static final String PURPOSE_SSO_CLIENT_SECRET = "sso.client_secret";
    private static final String PURPOSE_LLM_API_KEY = "llm.api_key";
    private static final String PROTOCOL_VERSION = "security.v1";

    @Value("${spring.application.name:datapillar-studio-service}")
    private String caller;

    @DubboReference(
            interfaceClass = CryptoService.class,
            version = "${datapillar.rpc.version:1.0.0}",
            group = "${datapillar.rpc.group:datapillar}"
    )
    private CryptoService cryptoService;

    public String encryptSsoClientSecret(String tenantCode, String plaintext) {
        return encrypt(tenantCode, PURPOSE_SSO_CLIENT_SECRET, plaintext);
    }

    public String decryptSsoClientSecret(String tenantCode, String ciphertext) {
        return decrypt(tenantCode, PURPOSE_SSO_CLIENT_SECRET, ciphertext);
    }

    public String encryptLlmApiKey(String tenantCode, String plaintext) {
        return encrypt(tenantCode, PURPOSE_LLM_API_KEY, plaintext);
    }

    public String decryptLlmApiKey(String tenantCode, String ciphertext) {
        return decrypt(tenantCode, PURPOSE_LLM_API_KEY, ciphertext);
    }

    public TenantKeySnapshot ensureTenantKey(String tenantCode) {
        String validTenantCode = requireTenantCode(tenantCode);
        EnsureTenantKeyResult result = invokeRpc(() -> cryptoService.ensureTenantKey(
                EnsureTenantKeyRequest.newBuilder()
                        .setMeta(buildMeta())
                        .setTenantCode(validTenantCode)
                        .build()));
        if (result == null) {
            throw new com.sunny.datapillar.common.exception.ServiceUnavailableException("密钥存储服务不可用");
        }
        if (result.hasError()) {
            throw mapRpcError(result.getError());
        }
        if (!result.hasData() || !result.getData().getSuccess() || !StringUtils.hasText(result.getData().getTenantCode())) {
            throw new com.sunny.datapillar.common.exception.InternalException("服务器内部错误");
        }
        return new TenantKeySnapshot(
                result.getData().getTenantCode(),
                result.getData().getPublicKeyPem(),
                result.getData().getKeyVersion(),
                result.getData().getFingerprint());
    }

    public TenantKeyStatus getTenantKeyStatus(String tenantCode) {
        String validTenantCode = requireTenantCode(tenantCode);
        GetTenantKeyStatusResult result = invokeRpc(() -> cryptoService.getTenantKeyStatus(
                GetTenantKeyStatusRequest.newBuilder()
                        .setMeta(buildMeta())
                        .setTenantCode(validTenantCode)
                        .build()));
        if (result == null) {
            throw new com.sunny.datapillar.common.exception.ServiceUnavailableException("密钥存储服务不可用");
        }
        if (result.hasError()) {
            throw mapRpcError(result.getError());
        }
        if (!result.hasData()) {
            throw new com.sunny.datapillar.common.exception.InternalException("服务器内部错误");
        }
        return new TenantKeyStatus(
                result.getData().getExists(),
                result.getData().getTenantCode(),
                result.getData().getStatus(),
                result.getData().getKeyVersion(),
                result.getData().getFingerprint());
    }

    private String encrypt(String tenantCode, String purpose, String plaintext) {
        String validTenantCode = requireTenantCode(tenantCode);
        if (!StringUtils.hasText(plaintext)) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        EncryptResult result = invokeRpc(() -> cryptoService.encrypt(
                EncryptRequest.newBuilder()
                        .setMeta(buildMeta())
                        .setTenantCode(validTenantCode)
                        .setPurpose(purpose)
                        .setPlaintext(plaintext)
                        .build()));
        if (result == null) {
            throw new com.sunny.datapillar.common.exception.ServiceUnavailableException("密钥存储服务不可用");
        }
        if (result.hasError()) {
            throw mapRpcError(result.getError());
        }
        if (!result.hasData() || !StringUtils.hasText(result.getData().getCiphertext())) {
            throw new com.sunny.datapillar.common.exception.InternalException("服务器内部错误");
        }
        return result.getData().getCiphertext();
    }

    private String decrypt(String tenantCode, String purpose, String ciphertext) {
        String validTenantCode = requireTenantCode(tenantCode);
        if (!StringUtils.hasText(ciphertext)) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        DecryptResult result = invokeRpc(() -> cryptoService.decrypt(
                DecryptRequest.newBuilder()
                        .setMeta(buildMeta())
                        .setTenantCode(validTenantCode)
                        .setPurpose(purpose)
                        .setCiphertext(ciphertext)
                        .build()));
        if (result == null) {
            throw new com.sunny.datapillar.common.exception.ServiceUnavailableException("密钥存储服务不可用");
        }
        if (result.hasError()) {
            throw mapRpcError(result.getError());
        }
        if (!result.hasData() || !StringUtils.hasText(result.getData().getPlaintext())) {
            throw new com.sunny.datapillar.common.exception.InternalException("服务器内部错误");
        }
        return result.getData().getPlaintext();
    }

    private RpcMeta buildMeta() {
        return RpcMeta.newBuilder()
                .setProtocolVersion(PROTOCOL_VERSION)
                .setCallerService(caller)
                .putAllAttrs(Map.of("caller", caller))
                .build();
    }

    private String requireTenantCode(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        return tenantCode.trim();
    }

    private <T> T invokeRpc(RpcCall<T> call) {
        try {
            return call.invoke();
        } catch (DatapillarRuntimeException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapTransportException(ex);
        }
    }

    private DatapillarRuntimeException mapTransportException(RuntimeException ex) {
        if (ex instanceof RpcException) {
            return new com.sunny.datapillar.common.exception.ServiceUnavailableException(
                    ex,
                    ErrorType.KEY_STORAGE_UNAVAILABLE,
                    Map.of(),
                    "密钥存储服务不可用");
        }
        return new com.sunny.datapillar.common.exception.InternalException(ex, ErrorType.INTERNAL_ERROR, Map.of(), "服务器内部错误");
    }

    private DatapillarRuntimeException mapRpcError(com.sunny.datapillar.common.rpc.security.v1.Error rpcError) {
        int code = rpcError.getCode();
        String type = rpcError.getType();
        Map<String, String> context = rpcError.getContextMap();
        String tenantCode = context.get("tenantCode");
        return switch (type) {
            case ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS ->
                    new com.sunny.datapillar.common.exception.AlreadyExistsException(type, context, buildTenantMessage("私钥文件已存在", tenantCode));
            case ErrorType.TENANT_PUBLIC_KEY_MISSING, ErrorType.TENANT_PRIVATE_KEY_MISSING ->
                    new com.sunny.datapillar.common.exception.ConflictException(type, context, buildTenantMessage("租户密钥数据不一致", tenantCode));
            case ErrorType.TENANT_KEY_NOT_FOUND ->
                    new com.sunny.datapillar.common.exception.NotFoundException(type, context, buildTenantMessage("租户密钥不存在", tenantCode));
            case ErrorType.PURPOSE_NOT_ALLOWED, ErrorType.CIPHERTEXT_INVALID, ErrorType.TENANT_KEY_INVALID ->
                    new com.sunny.datapillar.common.exception.BadRequestException(type, context, "参数错误");
            case ErrorType.KEY_STORAGE_UNAVAILABLE ->
                    new com.sunny.datapillar.common.exception.ServiceUnavailableException(type, context, "密钥存储服务不可用");
            default -> mapByCode(code, type, context, rpcError.getMessage(), rpcError.getRetryable());
        };
    }

    private DatapillarRuntimeException mapByCode(int code,
                                                 String type,
                                                 Map<String, String> context,
                                                 String message,
                                                 boolean retryable) {
        String resolvedMessage = StringUtils.hasText(message) ? message : "服务器内部错误";
        return switch (code) {
            case Code.BAD_REQUEST -> new com.sunny.datapillar.common.exception.BadRequestException(typeOrDefault(type, ErrorType.BAD_REQUEST), context, resolvedMessage);
            case Code.NOT_FOUND -> new com.sunny.datapillar.common.exception.NotFoundException(typeOrDefault(type, ErrorType.NOT_FOUND), context, resolvedMessage);
            case Code.CONFLICT -> new com.sunny.datapillar.common.exception.ConflictException(typeOrDefault(type, ErrorType.CONFLICT), context, resolvedMessage);
            case Code.SERVICE_UNAVAILABLE -> new com.sunny.datapillar.common.exception.ServiceUnavailableException(
                    typeOrDefault(type, ErrorType.SERVICE_UNAVAILABLE),
                    context,
                    resolvedMessage);
            case Code.BAD_GATEWAY -> new com.sunny.datapillar.common.exception.ServiceUnavailableException(
                    typeOrDefault(type, ErrorType.BAD_GATEWAY),
                    context,
                    resolvedMessage);
            default -> retryable
                    ? new com.sunny.datapillar.common.exception.ServiceUnavailableException(typeOrDefault(type, ErrorType.SERVICE_UNAVAILABLE), context, resolvedMessage)
                    : new com.sunny.datapillar.common.exception.InternalException(typeOrDefault(type, ErrorType.INTERNAL_ERROR), context, resolvedMessage);
        };
    }

    private String typeOrDefault(String type, String fallback) {
        if (StringUtils.hasText(type)) {
            return type;
        }
        return fallback;
    }

    private String buildTenantMessage(String baseMessage, String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            return baseMessage;
        }
        return baseMessage + ": " + tenantCode;
    }

    @FunctionalInterface
    private interface RpcCall<T> {
        T invoke();
    }

    public record TenantKeySnapshot(
            String tenantCode,
            String publicKeyPem,
            String keyVersion,
            String fingerprint) {
    }

    public record TenantKeyStatus(
            boolean exists,
            String tenantCode,
            String status,
            String keyVersion,
            String fingerprint) {
    }
}
