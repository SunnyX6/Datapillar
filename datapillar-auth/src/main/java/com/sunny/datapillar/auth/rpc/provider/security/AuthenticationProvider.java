package com.sunny.datapillar.auth.rpc.provider.security;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import com.sunny.datapillar.auth.security.AuthAssertionSigner;
import com.sunny.datapillar.auth.service.AuthService;
import com.sunny.datapillar.common.rpc.security.v1.AuthenticationService;
import com.sunny.datapillar.common.rpc.security.v1.CheckAuthenticationRequest;
import com.sunny.datapillar.common.rpc.security.v1.CheckAuthenticationResponse;
import com.sunny.datapillar.common.rpc.security.v1.DenyCode;
import com.sunny.datapillar.common.rpc.security.v1.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * 认证RPC服务提供者
 * 负责统一认证校验并输出主体上下文
 *
 * @author Sunny
 * @date 2026-02-19
 */
@Slf4j
@RequiredArgsConstructor
@DubboService(
        interfaceClass = AuthenticationService.class,
        group = "${datapillar.rpc.group:datapillar}",
        version = "${datapillar.rpc.version:1.0.0}"
)
public class AuthenticationProvider implements AuthenticationService {

    private static final String STUDIO_AUDIENCE = "datapillar-studio-service";
    private static final String AI_AUDIENCE = "datapillar-ai";

    private final AuthService authService;
    private final AuthAssertionSigner assertionSigner;

    @Override
    public CheckAuthenticationResponse checkAuthentication(CheckAuthenticationRequest request) {
        if (request == null || !StringUtils.hasText(request.getToken())) {
            return deny(DenyCode.TOKEN_MISSING, "缺少认证信息");
        }

        String method = normalizeMethod(request.getMethod());
        String path = normalizePath(request.getPath());

        try {
            AuthenticationContextResponse context = authService.resolveAuthenticationContext(request.getToken());
            if (context == null || context.getUserId() == null || context.getTenantId() == null) {
                return deny(DenyCode.TOKEN_INVALID, "认证主体缺失");
            }

            CheckAuthenticationResponse.Builder responseBuilder = CheckAuthenticationResponse.newBuilder()
                    .setAuthenticated(true)
                    .setDenyCode(DenyCode.DENY_CODE_UNSPECIFIED)
                    .setMessage("OK")
                    .setPrincipal(toPrincipal(context));
            String gatewayAssertion = buildGatewayAssertion(context, method, path);
            if (StringUtils.hasText(gatewayAssertion)) {
                responseBuilder.setGatewayAssertion(gatewayAssertion);
            }
            return responseBuilder.build();
        } catch (Exception ex) {
            log.warn("authentication_check_failed method={} path={} reason={}", method, path, ex.getMessage());
            return deny(mapDenyCode(ex), ex.getMessage() == null ? "认证判定失败" : ex.getMessage());
        }
    }

    @Override
    public CompletableFuture<CheckAuthenticationResponse> checkAuthenticationAsync(
            CheckAuthenticationRequest request) {
        return CompletableFuture.completedFuture(checkAuthentication(request));
    }

    private Principal toPrincipal(AuthenticationContextResponse context) {
        Principal.Builder builder = Principal.newBuilder()
                .setUserId(context.getUserId() == null ? 0L : context.getUserId())
                .setTenantId(context.getTenantId() == null ? 0L : context.getTenantId())
                .setImpersonation(Boolean.TRUE.equals(context.getImpersonation()));
        if (StringUtils.hasText(context.getTenantCode())) {
            builder.setTenantCode(context.getTenantCode());
        }
        if (StringUtils.hasText(context.getUsername())) {
            builder.setUsername(context.getUsername());
        }
        if (StringUtils.hasText(context.getEmail())) {
            builder.setEmail(context.getEmail());
        }
        if (context.getRoles() != null && !context.getRoles().isEmpty()) {
            builder.addAllRoles(context.getRoles());
        } else {
            builder.addAllRoles(Collections.emptyList());
        }
        if (context.getActorUserId() != null && context.getActorUserId() > 0) {
            builder.setActorUserId(context.getActorUserId());
        }
        if (context.getActorTenantId() != null && context.getActorTenantId() > 0) {
            builder.setActorTenantId(context.getActorTenantId());
        }
        if (StringUtils.hasText(context.getSessionId())) {
            builder.setSid(context.getSessionId());
        }
        if (StringUtils.hasText(context.getTokenId())) {
            builder.setJti(context.getTokenId());
        }
        return builder.build();
    }

    private String buildGatewayAssertion(AuthenticationContextResponse context, String method, String path) {
        String audience = resolveAssertionAudience(path);
        if (!StringUtils.hasText(audience)) {
            return null;
        }
        return assertionSigner.sign(new AuthAssertionSigner.AssertionPayload(
                context.getUserId(),
                context.getTenantId(),
                context.getTenantCode(),
                context.getUsername(),
                context.getEmail(),
                context.getRoles(),
                Boolean.TRUE.equals(context.getImpersonation()),
                context.getActorUserId(),
                context.getActorTenantId(),
                method,
                path
        ), audience);
    }

    private String resolveAssertionAudience(String path) {
        if (path.startsWith("/api/studio")) {
            return STUDIO_AUDIENCE;
        }
        if (path.startsWith("/api/ai")) {
            return AI_AUDIENCE;
        }
        return null;
    }

    private String normalizeMethod(String method) {
        if (!StringUtils.hasText(method)) {
            return "GET";
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private DenyCode mapDenyCode(Exception ex) {
        String reason = ex.getMessage();
        if (!StringUtils.hasText(reason)) {
            return DenyCode.SYSTEM_UNAVAILABLE;
        }
        if (reason.contains("缺少认证信息")) {
            return DenyCode.TOKEN_MISSING;
        }
        if (reason.contains("Token已过期")) {
            return DenyCode.TOKEN_EXPIRED;
        }
        if (reason.contains("Token已失效") || reason.contains("session_inactive")) {
            return DenyCode.SESSION_REVOKED;
        }
        if (reason.contains("租户已被禁用")) {
            return DenyCode.TENANT_DISABLED;
        }
        if (reason.contains("用户已被禁用")) {
            return DenyCode.USER_DISABLED;
        }
        if (reason.contains("无权限") || reason.contains("权限")) {
            return DenyCode.PERMISSION_DENIED;
        }
        if (reason.contains("Token") || reason.contains("未授权")) {
            return DenyCode.TOKEN_INVALID;
        }
        return DenyCode.SYSTEM_UNAVAILABLE;
    }

    private CheckAuthenticationResponse deny(DenyCode denyCode, String message) {
        CheckAuthenticationResponse.Builder builder = CheckAuthenticationResponse.newBuilder()
                .setAuthenticated(false)
                .setDenyCode(denyCode == null ? DenyCode.SYSTEM_UNAVAILABLE : denyCode);
        if (StringUtils.hasText(message)) {
            builder.setMessage(message);
        }
        return builder.build();
    }
}
