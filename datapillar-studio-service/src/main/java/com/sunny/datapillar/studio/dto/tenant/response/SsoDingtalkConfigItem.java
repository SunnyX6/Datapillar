package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "SsoConfigDingtalkConfig")
public class SsoDingtalkConfigItem {

    private String clientId;

    private String clientSecret;

    private String redirectUri;

    private String scope;

    private String responseType;

    private String prompt;

    private String corpId;
}
