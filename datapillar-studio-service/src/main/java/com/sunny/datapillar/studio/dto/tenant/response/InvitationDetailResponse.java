package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@Schema(name = "InvitationDetailResponse")
public class InvitationDetailResponse {

    private String inviteCode;

    private String tenantName;

    private Long roleId;

    private String roleName;

    private String inviterName;

    private OffsetDateTime expiresAt;

    private Integer status;
}
