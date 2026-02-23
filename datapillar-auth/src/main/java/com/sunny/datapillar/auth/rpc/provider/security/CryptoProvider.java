package com.sunny.datapillar.auth.rpc.provider.security;

import com.sunny.datapillar.auth.security.keystore.TenantKeyService;
import com.sunny.datapillar.auth.security.keystore.TenantKeyService.TenantKeySnapshot;
import com.sunny.datapillar.auth.security.keystore.TenantKeyService.TenantKeyStatus;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.crypto.SecretCodec;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.rpc.security.v1.CryptoService;
import com.sunny.datapillar.common.rpc.security.v1.DecryptRequest;
import com.sunny.datapillar.common.rpc.security.v1.DecryptResponse;
import com.sunny.datapillar.common.rpc.security.v1.DecryptResult;
import com.sunny.datapillar.common.rpc.security.v1.EncryptRequest;
import com.sunny.datapillar.common.rpc.security.v1.EncryptResponse;
import com.sunny.datapillar.common.rpc.security.v1.EncryptResult;
import com.sunny.datapillar.common.rpc.security.v1.EnsureTenantKeyRequest;
import com.sunny.datapillar.common.rpc.security.v1.EnsureTenantKeyResponse;
import com.sunny.datapillar.common.rpc.security.v1.EnsureTenantKeyResult;
import com.sunny.datapillar.common.rpc.security.v1.GetTenantKeyStatusRequest;
import com.sunny.datapillar.common.rpc.security.v1.GetTenantKeyStatusResponse;
import com.sunny.datapillar.common.rpc.security.v1.GetTenantKeyStatusResult;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

/**
 * 加解密RPC服务提供者
 * 负责密钥加解密与私钥管理能力输出
 *
 * @author Sunny
 * @date 2026-02-19
 */
@RequiredArgsConstructor
@DubboService(
        interfaceClass = CryptoService.class,
        group = "${datapillar.rpc.group:datapillar}",
        version = "${datapillar.rpc.version:1.0.0}"
)
public class CryptoProvider implements CryptoService {

    private static final Set<String> ALLOWED_PURPOSES = Set.of("sso.client_secret", "llm.api_key");

    private final TenantKeyService tenantKeyService;

    @Override
    public EnsureTenantKeyResult ensureTenantKey(EnsureTenantKeyRequest request) {
        String tenantCode = request == null ? null : request.getTenantCode();
        try {
            String validTenantCode = requireTenantCode(tenantCode);
            TenantKeySnapshot snapshot = tenantKeyService.ensureTenantKey(validTenantCode);
            EnsureTenantKeyResponse data = EnsureTenantKeyResponse.newBuilder()
                    .setSuccess(true)
                    .setTenantCode(snapshot.tenantCode())
                    .setPublicKeyPem(snapshot.publicKeyPem())
                    .setKeyVersion(snapshot.keyVersion())
                    .setFingerprint(snapshot.fingerprint())
                    .build();
            return EnsureTenantKeyResult.newBuilder().setData(data).build();
        } catch (DatapillarRuntimeException ex) {
            return EnsureTenantKeyResult.newBuilder().setError(toRpcError(ex)).build();
        } catch (Exception ex) {
            return EnsureTenantKeyResult.newBuilder().setError(toRpcError(wrapStorageFailure(ex, tenantCode))).build();
        }
    }

    @Override
    public CompletableFuture<EnsureTenantKeyResult> ensureTenantKeyAsync(EnsureTenantKeyRequest request) {
        return CompletableFuture.completedFuture(ensureTenantKey(request));
    }

    @Override
    public GetTenantKeyStatusResult getTenantKeyStatus(GetTenantKeyStatusRequest request) {
        String tenantCode = request == null ? null : request.getTenantCode();
        try {
            String validTenantCode = requireTenantCode(tenantCode);
            TenantKeyStatus status = tenantKeyService.getStatus(validTenantCode);
            GetTenantKeyStatusResponse.Builder builder = GetTenantKeyStatusResponse.newBuilder()
                    .setExists(status.exists())
                    .setTenantCode(status.tenantCode())
                    .setStatus(status.status());
            if (StringUtils.hasText(status.keyVersion())) {
                builder.setKeyVersion(status.keyVersion());
            }
            if (StringUtils.hasText(status.fingerprint())) {
                builder.setFingerprint(status.fingerprint());
            }
            return GetTenantKeyStatusResult.newBuilder().setData(builder.build()).build();
        } catch (DatapillarRuntimeException ex) {
            return GetTenantKeyStatusResult.newBuilder().setError(toRpcError(ex)).build();
        } catch (Exception ex) {
            return GetTenantKeyStatusResult.newBuilder().setError(toRpcError(wrapStorageFailure(ex, tenantCode))).build();
        }
    }

    @Override
    public CompletableFuture<GetTenantKeyStatusResult> getTenantKeyStatusAsync(GetTenantKeyStatusRequest request) {
        return CompletableFuture.completedFuture(getTenantKeyStatus(request));
    }

    @Override
    public EncryptResult encrypt(EncryptRequest request) {
        String tenantCode = request == null ? null : request.getTenantCode();
        try {
            String validTenantCode = requireTenantCode(tenantCode);
            requirePurpose(request == null ? null : request.getPurpose());
            String plaintext = request == null ? null : request.getPlaintext();
            if (!StringUtils.hasText(plaintext)) {
                throw new BadRequestException(
                        ErrorType.TENANT_KEY_INVALID,
                        Map.of("tenantCode", validTenantCode),
                        "参数错误");
            }

            String publicKeyPem = tenantKeyService.loadPublicKey(validTenantCode);
            String ciphertext = SecretCodec.encrypt(publicKeyPem, plaintext);
            EncryptResponse data = EncryptResponse.newBuilder().setCiphertext(ciphertext).build();
            return EncryptResult.newBuilder().setData(data).build();
        } catch (IllegalArgumentException ex) {
            return EncryptResult.newBuilder().setError(toRpcError(new InternalException(
                    ex,
                    ErrorType.TENANT_KEY_INVALID,
                    tenantContext(tenantCode),
                    "租户密钥无效"))).build();
        } catch (DatapillarRuntimeException ex) {
            return EncryptResult.newBuilder().setError(toRpcError(ex)).build();
        } catch (Exception ex) {
            return EncryptResult.newBuilder().setError(toRpcError(wrapStorageFailure(ex, tenantCode))).build();
        }
    }

    @Override
    public CompletableFuture<EncryptResult> encryptAsync(EncryptRequest request) {
        return CompletableFuture.completedFuture(encrypt(request));
    }

    @Override
    public DecryptResult decrypt(DecryptRequest request) {
        String tenantCode = request == null ? null : request.getTenantCode();
        try {
            String validTenantCode = requireTenantCode(tenantCode);
            requirePurpose(request == null ? null : request.getPurpose());
            String ciphertext = request == null ? null : request.getCiphertext();
            if (!StringUtils.hasText(ciphertext)) {
                throw new BadRequestException(
                        ErrorType.CIPHERTEXT_INVALID,
                        Map.of("tenantCode", validTenantCode),
                        "参数错误");
            }

            byte[] privateKey = tenantKeyService.loadPrivateKey(validTenantCode);
            String plaintext = SecretCodec.decrypt(privateKey, ciphertext);
            DecryptResponse data = DecryptResponse.newBuilder().setPlaintext(plaintext).build();
            return DecryptResult.newBuilder().setData(data).build();
        } catch (IllegalArgumentException ex) {
            return DecryptResult.newBuilder().setError(toRpcError(new BadRequestException(
                    ErrorType.CIPHERTEXT_INVALID,
                    tenantContext(tenantCode),
                    "参数错误"))).build();
        } catch (DatapillarRuntimeException ex) {
            return DecryptResult.newBuilder().setError(toRpcError(ex)).build();
        } catch (Exception ex) {
            return DecryptResult.newBuilder().setError(toRpcError(wrapStorageFailure(ex, tenantCode))).build();
        }
    }

    @Override
    public CompletableFuture<DecryptResult> decryptAsync(DecryptRequest request) {
        return CompletableFuture.completedFuture(decrypt(request));
    }

    private String requireTenantCode(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new BadRequestException(ErrorType.TENANT_KEY_INVALID, Map.of(), "参数错误");
        }
        return tenantCode.trim();
    }

    private String requirePurpose(String purpose) {
        if (!StringUtils.hasText(purpose)) {
            throw new BadRequestException(ErrorType.PURPOSE_NOT_ALLOWED, Map.of(), "参数错误");
        }
        String normalized = purpose.trim();
        if (!ALLOWED_PURPOSES.contains(normalized)) {
            throw new BadRequestException(
                    ErrorType.PURPOSE_NOT_ALLOWED,
                    Map.of("purpose", normalized),
                    "参数错误");
        }
        return normalized;
    }

    private DatapillarRuntimeException wrapStorageFailure(Exception ex, String tenantCode) {
        return new ServiceUnavailableException(
                ex,
                ErrorType.KEY_STORAGE_UNAVAILABLE,
                tenantContext(tenantCode),
                "密钥存储服务不可用");
    }

    private Map<String, String> tenantContext(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            return Map.of();
        }
        return Map.of("tenantCode", tenantCode.trim());
    }

    private com.sunny.datapillar.common.rpc.security.v1.Error toRpcError(DatapillarRuntimeException exception) {
        com.sunny.datapillar.common.rpc.security.v1.Error.Builder builder =
                com.sunny.datapillar.common.rpc.security.v1.Error.newBuilder()
                        .setCode(exception.getCode())
                        .setType(StringUtils.hasText(exception.getType()) ? exception.getType() : ErrorType.INTERNAL_ERROR)
                        .setMessage(StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "服务器内部错误")
                        .setRetryable(exception.isRetryable());
        exception.getContext().forEach(builder::putContext);
        return builder.build();
    }
}
