package com.sunny.datapillar.studio.module.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 单点登录Identity数据传输对象
 * 定义单点登录Identity数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class SsoIdentityDto {

    @Data
    @Schema(name = "SsoIdentityBindByCodeRequest")
    public static class BindByCodeRequest {
        @NotNull(message = "用户ID不能为空")
        private Long userId;

        @NotBlank(message = "provider不能为空")
        private String provider;

        @NotBlank(message = "authCode不能为空")
        private String authCode;
    }

    @Data
    @Schema(name = "SsoIdentityItem")
    public static class Item {
        private Long id;
        private Long userId;
        private String provider;
        private String externalUserId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
