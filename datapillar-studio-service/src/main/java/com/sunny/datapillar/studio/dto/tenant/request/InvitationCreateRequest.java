package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@Schema(name = "InvitationCreate")
public class InvitationCreateRequest {

  @NotNull(message = "Role cannot be empty")
  private Long roleId;

  @NotNull(message = "Expiration time cannot be empty")
  private OffsetDateTime expiresAt;
}
