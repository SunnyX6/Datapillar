package com.sunny.datapillar.studio.module.setup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 初始化数据传输对象
 * 定义初始化数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class SetupDto {

    @Data
    @Schema(name = "SetupStatusResponse")
    public static class StatusResponse {
        /**
         * 数据库迁移是否就绪
         */
        private boolean schemaReady;

        /**
         * 是否已完成初始化
         */
        private boolean initialized;

        /**
         * 当前步骤编码
         */
        private String currentStep;

        /**
         * 初始化步骤列表
         */
        private List<StepStatus> steps;
    }

    @Data
    @Schema(name = "SetupStepStatus")
    public static class StepStatus {
        /**
         * 步骤编码
         */
        private String code;

        /**
         * 步骤名称
         */
        private String name;

        /**
         * 步骤说明
         */
        private String description;

        /**
         * 步骤状态：PENDING/IN_PROGRESS/COMPLETED
         */
        private String status;
    }

    @Data
    @Schema(name = "SetupInitializeRequest")
    public static class InitializeRequest {

        /**
         * 企业/组织名称
         */
        @NotBlank(message = "企业/组织名称不能为空")
        @Size(max = 128, message = "企业/组织名称长度不能超过128个字符")
        private String organizationName;

        /**
         * 管理员展示名
         */
        @NotBlank(message = "管理员名称不能为空")
        @Size(max = 64, message = "管理员名称长度不能超过64个字符")
        private String adminName;

        @NotBlank(message = "管理员用户名不能为空")
        @Size(max = 64, message = "管理员用户名长度不能超过64个字符")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "管理员用户名仅支持字母、数字、下划线、点和中划线")
        private String username;

        @NotBlank(message = "管理员邮箱不能为空")
        @Size(max = 128, message = "管理员邮箱长度不能超过128个字符")
        @Email(message = "管理员邮箱格式不正确")
        private String email;

        @NotBlank(message = "管理员密码不能为空")
        @Size(min = 8, max = 128, message = "管理员密码长度必须在8到128个字符之间")
        private String password;
    }

    @Data
    @Schema(name = "SetupInitializeResponse")
    public static class InitializeResponse {
        private Long tenantId;
        private Long userId;
    }
}
