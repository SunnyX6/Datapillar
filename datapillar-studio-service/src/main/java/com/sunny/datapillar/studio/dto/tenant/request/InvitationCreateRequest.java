package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@Schema(name = "InvitationCreate")
public class InvitationCreateRequest {

    @NotNull(message = "角色不能为空")
    private Long roleId;

    @NotNull(message = "过期时间不能为空")
    private OffsetDateTime expiresAt;
}
