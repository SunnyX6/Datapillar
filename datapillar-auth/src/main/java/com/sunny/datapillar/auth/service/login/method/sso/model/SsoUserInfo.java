package com.sunny.datapillar.auth.service.login.method.sso.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * 单点登录用户Info组件
 * 负责单点登录用户Info核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoUserInfo {
    private String externalUserId;
    private String unionId;
    private String openId;
    private String mobile;
    private String email;
    private String nick;
    private String corpId;
    private String rawJson;
}
