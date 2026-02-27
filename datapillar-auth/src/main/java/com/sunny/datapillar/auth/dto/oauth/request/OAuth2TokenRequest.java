package com.sunny.datapillar.auth.dto.oauth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthOAuth2TokenRequest")
public class OAuth2TokenRequest {

    private String grantType;

    private String username;

    private String password;

    private String clientId;

    private String clientSecret;
}
