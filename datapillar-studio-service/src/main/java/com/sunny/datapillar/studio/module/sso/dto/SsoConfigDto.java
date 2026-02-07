package com.sunny.datapillar.studio.module.sso.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SSO 配置 DTO
 */
public class SsoConfigDto {

    @Data
    public static class Create {
        @NotBlank(message = "SSO提供方不能为空")
        @Size(max = 32, message = "SSO提供方长度不能超过32个字符")
        private String provider;

        @Size(max = 255, message = "基础URL长度不能超过255个字符")
        private String baseUrl;

        @NotBlank(message = "配置不能为空")
        private String configJson;

        private Integer status;
    }

    @Data
    public static class Update {
        @Size(max = 255, message = "基础URL长度不能超过255个字符")
        private String baseUrl;

        private String configJson;

        private Integer status;
    }

    @Data
    public static class Response {
        private Long id;
        private Long tenantId;
        private String provider;
        private String baseUrl;
        private Integer status;
        private String configJson;
    }
}
