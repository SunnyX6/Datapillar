package com.sunny.datapillar.auth.service.login.method.sso.provider;

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
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoQrResponse;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoToken;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoUserInfo;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;

import lombok.RequiredArgsConstructor;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * Dingtalk单点登录提供器组件
 * 负责Dingtalk单点登录提供器核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
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
            throw new BadRequestException("参数错误");
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
                throw new InternalException("SSO请求失败: %s", "accessToken为空");
            }
            Map<String, Object> raw = toMap(response.getBody());
            return new SsoToken(response.getBody().getAccessToken(), null, raw);
        } catch (DatapillarRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalException("SSO请求失败: %s", e.getMessage());
        }
    }

    @Override
    public SsoUserInfo fetchUserInfo(SsoProviderConfig config, SsoToken token) {
        if (token == null || token.getAccessToken() == null || token.getAccessToken().isBlank()) {
            throw new InternalException("SSO请求失败: %s", "accessToken为空");
        }
        try {
            Client client = new Client(buildOpenApiConfig());
            GetUserHeaders headers = new GetUserHeaders();
            headers.xAcsDingtalkAccessToken = token.getAccessToken();
            GetUserResponse response = client.getUserWithOptions("me", headers, new RuntimeOptions());
            if (response == null || response.getBody() == null) {
                throw new InternalException("SSO请求失败: %s", "用户信息为空");
            }
            String unionId = response.getBody().getUnionId();
            String openId = response.getBody().getOpenId();
            String externalUserId = unionId;
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
        } catch (DatapillarRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalException("SSO请求失败: %s", e.getMessage());
        }
    }

    @Override
    public String extractExternalUserId(SsoUserInfo userInfo) {
        return userInfo == null ? null : userInfo.getUnionId();
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
            throw new InternalException(e, "SSO请求失败: %s", "dingtalk_token_response_convert_failed");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new InternalException(e, "SSO请求失败: %s", "dingtalk_user_info_serialize_failed");
        }
    }

    private String pickOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

}
