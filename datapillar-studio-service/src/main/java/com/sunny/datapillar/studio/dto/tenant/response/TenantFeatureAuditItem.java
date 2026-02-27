package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "TenantFeatureAuditItem")
public class TenantFeatureAuditItem {

    private Long id;

    private Long tenantId;

    private String action;

    private String detail;

    private Long operatorUserId;

    private LocalDateTime createdAt;
}
