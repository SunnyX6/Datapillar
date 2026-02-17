package com.sunny.datapillar.studio.module.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 租户邀请数据传输对象
 * 定义租户邀请数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class TenantInvitationDto {

    @Schema(name = "TenantInvitationCreateResponse")
    public static class CreateResponse extends InvitationDto.CreateResponse {
    }
}
