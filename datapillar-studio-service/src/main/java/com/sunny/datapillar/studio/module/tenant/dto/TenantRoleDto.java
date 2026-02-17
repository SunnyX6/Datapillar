package com.sunny.datapillar.studio.module.tenant.dto;

import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 租户角色数据传输对象
 * 定义租户角色数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class TenantRoleDto {

    @Schema(name = "TenantRoleResponse")
    public static class Response extends RoleDto.Response {
    }
}
