package com.sunny.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRespDto {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String username;
    private String email;

    // 用户角色代码列表
    private List<String> roles;

    // 用户权限代码列表
    private List<String> permissions;

    // 用户菜单列表
    private List<MenuDto> menus;

    /**
     * 菜单DTO（内部类）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuDto {
        private Long id;
        private String name;
        private String path;
        private String icon;
        private String permissionCode;
        private List<MenuDto> children;
    }
}
