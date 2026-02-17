package com.sunny.datapillar.studio.module.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 租户单点登录数据传输对象
 * 定义租户单点登录数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class TenantSsoDto {

    @Schema(name = "TenantSsoConfigResponse")
    public static class ConfigResponse extends SsoConfigDto.Response {
    }

    @Schema(name = "TenantSsoIdentityItem")
    public static class IdentityItem extends SsoIdentityDto.Item {
    }
}
