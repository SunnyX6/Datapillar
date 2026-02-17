package com.sunny.datapillar.studio.module.tenant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 单点登录配置数据传输对象
 * 定义单点登录配置数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class SsoConfigDto {

    @Data
    @Schema(name = "SsoConfigCreate")
    public static class Create {
        @NotBlank(message = "SSO提供方不能为空")
        @Size(max = 32, message = "SSO提供方长度不能超过32个字符")
        private String provider;

        @Size(max = 255, message = "基础URL长度不能超过255个字符")
        private String baseUrl;

        @Valid
        private DingtalkConfig config;

        private Integer status;
    }

    @Data
    @Schema(name = "SsoConfigUpdate")
    public static class Update {
        @Size(max = 255, message = "基础URL长度不能超过255个字符")
        private String baseUrl;

        @Valid
        private DingtalkConfig config;

        private Integer status;
    }

    @Data
    @Schema(name = "SsoConfigDingtalkConfig")
    public static class DingtalkConfig {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String scope;
        private String responseType;
        private String prompt;
        private String corpId;
    }

    @Data
    @Schema(name = "SsoConfigResponse")
    public static class Response {
        private Long id;
        private Long tenantId;
        private String provider;
        private String baseUrl;
        private Integer status;
        private Boolean hasClientSecret;
        private DingtalkConfig config;
        private LocalDateTime updatedAt;
    }
}
