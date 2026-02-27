package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@Schema(name = "InvitationCreateResponse")
public class InvitationCreateResponse {

    private Long invitationId;

    private String inviteCode;

    private String inviteUri;

    private OffsetDateTime expiresAt;

    private String tenantName;

    private Long roleId;

    private String roleName;

    private String inviterName;
}
