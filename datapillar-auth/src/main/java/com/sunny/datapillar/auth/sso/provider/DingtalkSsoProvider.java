package com.sunny.datapillar.auth.sso.provider;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.aliyun.dingtalkcontact_1_0.Client;
import com.aliyun.dingtalkcontact_1_0.models.GetUserHeaders;
import com.aliyun.dingtalkcontact_1_0.models.GetUserResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.sso.SsoProvider;
import com.sunny.datapillar.auth.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.sso.model.SsoQrResponse;
import com.sunny.datapillar.auth.sso.model.SsoToken;
import com.sunny.datapillar.auth.sso.model.SsoUserInfo;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 钉钉 SSO Provider
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingtalkSsoProvider implements SsoProvider {

    private static final String DEFAULT_SCOPE = "openid corpid";
    private static final String DEFAULT_RESPONSE_TYPE = "code";
    private static final String DEFAULT_PROMPT = "consent";

    private final ObjectMapper objectMapper;

    @Override
    public String provider() {
        return "dingtalk";
    }

    @Override
    public SsoQrResponse buildQr(SsoProviderConfig config, String state) {
        String clientId = config.getRequiredString("clientId");
        String redirectUri = config.getRequiredString("redirectUri");
        String scope = pickOrDefault(config.getOptionalString("scope"), DEFAULT_SCOPE);
        String responseType = pickOrDefault(config.getOptionalString("responseType"), DEFAULT_RESPONSE_TYPE);
        String prompt = pickOrDefault(config.getOptionalString("prompt"), DEFAULT_PROMPT);
        String corpId = config.getOptionalString("corpId");

        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", clientId);
        payload.put("redirectUri", redirectUri);
        payload.put("scope", scope);
        payload.put("responseType", responseType);
        payload.put("state", state);
        if (prompt != null) {
            payload.put("prompt", prompt);
        }
        if (corpId != null) {
            payload.put("corpId", corpId);
        }
        return new SsoQrResponse("SDK", state, payload);
    }

    @Override
    public SsoToken exchangeCode(SsoProviderConfig config, String authCode) {
        if (authCode == null || authCode.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_ARGUMENT);
        }
        String clientId = config.getRequiredString("clientId");
        String clientSecret = config.getRequiredString("clientSecret");
        try {
            com.aliyun.dingtalkoauth2_1_0.Client client = new com.aliyun.dingtalkoauth2_1_0.Client(buildOpenApiConfig());
            GetUserTokenRequest request = new GetUserTokenRequest()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setCode(authCode)
                    .setGrantType("authorization_code");
            GetUserTokenResponse response = client.getUserToken(request);
            if (response == null || response.getBody() == null || response.getBody().getAccessToken() == null
                    || response.getBody().getAccessToken().isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_SSO_REQUEST_FAILED, "accessToken为空");
            }
            Map<String, Object> raw = toMap(response.getBody());
            return new SsoToken(response.getBody().getAccessToken(), null, raw);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AUTH_SSO_REQUEST_FAILED, e.getMessage());
        }
    }

    @Override
    public SsoUserInfo fetchUserInfo(SsoProviderConfig config, SsoToken token) {
        if (token == null || token.getAccessToken() == null || token.getAccessToken().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_SSO_REQUEST_FAILED, "accessToken为空");
        }
        try {
            Client client = new Client(buildOpenApiConfig());
            GetUserHeaders headers = new GetUserHeaders();
            headers.xAcsDingtalkAccessToken = token.getAccessToken();
            GetUserResponse response = client.getUserWithOptions("me", headers, new RuntimeOptions());
            if (response == null || response.getBody() == null) {
                throw new BusinessException(ErrorCode.AUTH_SSO_REQUEST_FAILED, "用户信息为空");
            }
            String unionId = response.getBody().getUnionId();
            String openId = response.getBody().getOpenId();
            String externalUserId = pickFirstNotBlank(unionId, openId);
            return SsoUserInfo.builder()
                    .externalUserId(externalUserId)
                    .unionId(unionId)
                    .openId(openId)
                    .mobile(response.getBody().getMobile())
                    .email(response.getBody().getEmail())
                    .nick(response.getBody().getNick())
                    .corpId(config.getOptionalString("corpId"))
                    .rawJson(toJson(response.getBody()))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AUTH_SSO_REQUEST_FAILED, e.getMessage());
        }
    }

    private Config buildOpenApiConfig() {
        Config config = new Config();
        config.protocol = "https";
        config.regionId = "central";
        return config;
    }

    private Map<String, Object> toMap(Object value) {
        try {
            return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (IllegalArgumentException e) {
            log.warn("钉钉响应转换失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("钉钉用户信息序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private String pickOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private String pickFirstNotBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
