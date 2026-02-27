package com.sunny.datapillar.studio.module.tenant.service.sso.provider;

import com.aliyun.dingtalkcontact_1_0.Client;
import com.aliyun.dingtalkcontact_1_0.models.GetUserHeaders;
import com.aliyun.dingtalkcontact_1_0.models.GetUserResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.studio.exception.sso.InvalidSsoIdentityRequestException;
import com.sunny.datapillar.studio.exception.sso.SsoProviderUnavailableException;
import com.sunny.datapillar.studio.module.tenant.service.sso.provider.model.DingtalkUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DingtalkBinding客户端
 * 负责DingtalkBinding客户端调用与协议封装
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class DingtalkBindingClient {

    private final ObjectMapper objectMapper;

    public DingtalkUserInfo fetchUserInfo(String clientId, String clientSecret, String authCode) {
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(authCode)) {
            throw new InvalidSsoIdentityRequestException();
        }
        try {
            com.aliyun.dingtalkoauth2_1_0.Client oauthClient = new com.aliyun.dingtalkoauth2_1_0.Client(buildConfig());
            GetUserTokenRequest request = new GetUserTokenRequest()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setCode(authCode)
                    .setGrantType("authorization_code");
            GetUserTokenResponse tokenResponse = oauthClient.getUserToken(request);
            String accessToken = tokenResponse == null || tokenResponse.getBody() == null
                    ? null
                    : tokenResponse.getBody().getAccessToken();
            if (isBlank(accessToken)) {
                throw new SsoProviderUnavailableException();
            }

            Client contactClient = new Client(buildConfig());
            GetUserHeaders headers = new GetUserHeaders();
            headers.xAcsDingtalkAccessToken = accessToken;
            GetUserResponse userResponse = contactClient.getUserWithOptions("me", headers, new RuntimeOptions());
            if (userResponse == null || userResponse.getBody() == null) {
                throw new SsoProviderUnavailableException();
            }
            String unionId = userResponse.getBody().getUnionId();
            if (isBlank(unionId)) {
                throw new SsoProviderUnavailableException();
            }
            return new DingtalkUserInfo(unionId, objectMapper.writeValueAsString(userResponse.getBody()));
        } catch (DatapillarRuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SsoProviderUnavailableException(ex);
        }
    }

    private Config buildConfig() {
        Config config = new Config();
        config.protocol = "https";
        config.regionId = "central";
        return config;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
