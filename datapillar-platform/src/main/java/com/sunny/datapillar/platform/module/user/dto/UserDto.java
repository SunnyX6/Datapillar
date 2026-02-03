package com.sunny.datapillar.platform.module.user.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户 DTO
 *
 * @author sunny
 */
public class UserDto {

    @Data
    public static class Create {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度必须在3-64个字符之间")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 255, message = "密码长度必须在6-255个字符之间")
        private String password;

        @Size(max = 64, message = "昵称长度不能超过64个字符")
        private String nickname;

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过128个字符")
        private String email;

        @Size(max = 32, message = "手机号长度不能超过32个字符")
        private String phone;

        private Integer status;

        private List<Long> roleIds;
    }

    @Data
    public static class Update {
        @Size(min = 3, max = 64, message = "用户名长度必须在3-64个字符之间")
        private String username;

        @Size(min = 6, max = 255, message = "密码长度必须在6-255个字符之间")
        private String password;

        @Size(max = 64, message = "昵称长度不能超过64个字符")
        private String nickname;

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过128个字符")
        private String email;

        @Size(max = 32, message = "手机号长度不能超过32个字符")
        private String phone;

        private Integer status;

        private List<Long> roleIds;
    }

    @Data
    public static class UpdateProfile {
        @Size(max = 50, message = "昵称长度不能超过50个字符")
        private String nickname;

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱长度不能超过100个字符")
        private String email;

        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
    }

    @Data
    public static class Response {
        private Long id;
        private String username;
        private String nickname;
        private String email;
        private String phone;
        private Integer status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<RoleDto.Response> roles;
        private List<String> permissions;
    }
}
