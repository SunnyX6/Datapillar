package com.sunny.datapillar.studio.dto.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "LlmUserModelGrantRequest")
public class LlmUserModelGrantRequest {

  @NotBlank(message = "permission_code cannot be empty")
  @Size(max = 32, message = "permission_code The length cannot exceed 32")
  private String permissionCode;

  private Boolean isDefault;

  private LocalDateTime expiresAt;
}
