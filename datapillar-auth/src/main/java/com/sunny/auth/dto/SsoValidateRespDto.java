package com.sunny.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SSO Token 验证响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoValidateRespDto {
    /**
     * 验证是否成功
     */
    private Boolean valid;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色列表（预留给XXL-Job等系统使用）
     */
    private List<String> roles;

    /**
     * 失败原因
     */
    private String message;

    public static SsoValidateRespDto success(Long userId, String username, String email) {
        return SsoValidateRespDto.builder()
                .valid(true)
                .userId(userId)
                .username(username)
                .email(email)
                .build();
    }

    public static SsoValidateRespDto failure(String message) {
        return SsoValidateRespDto.builder()
                .valid(false)
                .message(message)
                .build();
    }
}
