package com.sunny.datapillar.studio.module.tenant.service.sso.provider.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Dingtalk用户Info组件
 * 负责Dingtalk用户Info核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@AllArgsConstructor
public class DingtalkUserInfo {

    private String unionId;
    private String rawJson;
}
