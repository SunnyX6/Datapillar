package com.sunny.datapillar.auth.sso.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
