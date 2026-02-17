package com.sunny.datapillar.studio.module.tenant.dto;

import com.sunny.datapillar.studio.module.user.dto.UserDto;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 租户Member数据传输对象
 * 定义租户Member数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class TenantMemberDto {

    @Schema(name = "TenantMemberResponse")
    public static class Response extends UserDto.Response {
    }
}
