package com.sunny.datapillar.studio.module.tenant.dto;

import java.time.LocalDateTime;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 租户功能Audit数据传输对象
 * 定义租户功能Audit数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class TenantFeatureAuditDto {

    @Data
    @Schema(name = "TenantFeatureAuditItem")
    public static class Item {
        private Long id;
        private Long tenantId;
        private String action;
        private String detail;
        private Long operatorUserId;
        private LocalDateTime createdAt;
    }
}
