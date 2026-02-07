package com.sunny.datapillar.studio.module.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户 DTO
 */
public class TenantDto {

    @Data
    public static class Create {
        @NotBlank(message = "租户编码不能为空")
        @Size(max = 64, message = "租户编码长度不能超过64个字符")
        private String code;

        @NotBlank(message = "租户名称不能为空")
        @Size(max = 128, message = "租户名称长度不能超过128个字符")
        private String name;

        @NotBlank(message = "租户类型不能为空")
        @Size(max = 32, message = "租户类型长度不能超过32个字符")
        private String type;

        private Long parentId;
    }

    @Data
    public static class Update {
        @Size(max = 128, message = "租户名称长度不能超过128个字符")
        private String name;

        @Size(max = 32, message = "租户类型长度不能超过32个字符")
        private String type;

        private Long parentId;
    }

    @Data
    public static class StatusUpdate {
        private Integer status;
    }

    @Data
    public static class Response {
        private Long id;
        private Long parentId;
        private String code;
        private String name;
        private String type;
        private Integer status;
        private Integer level;
        private String path;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
