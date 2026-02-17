package com.sunny.datapillar.auth.service.login.method.sso.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * 单点登录Qr响应模型
 * 定义单点登录Qr响应数据结构
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoQrResponse {
    /**
     * SDK / URL
     */
    private String type;

    /**
     * 登录 state
     */
    private String state;

    /**
     * 扫码/授权配置
     */
    private Map<String, Object> payload;
}
