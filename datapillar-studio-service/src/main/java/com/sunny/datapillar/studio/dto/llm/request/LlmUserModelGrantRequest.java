package com.sunny.datapillar.studio.dto.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "LlmUserModelGrantRequest")
public class LlmUserModelGrantRequest {

    @NotBlank(message = "permission_code 不能为空")
    @Size(max = 32, message = "permission_code 长度不能超过 32")
    private String permissionCode;

    private Boolean isDefault;

    private LocalDateTime expiresAt;
}
