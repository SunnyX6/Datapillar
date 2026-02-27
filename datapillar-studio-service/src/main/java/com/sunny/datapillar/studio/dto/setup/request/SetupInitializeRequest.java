package com.sunny.datapillar.studio.dto.setup.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "SetupInitializeRequest")
public class SetupInitializeRequest {

    @NotBlank(message = "企业/组织名称不能为空")
    @Size(max = 128, message = "企业/组织名称长度不能超过128个字符")
    private String organizationName;

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
