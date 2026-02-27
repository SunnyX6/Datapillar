package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "SsoIdentityItem")
public class SsoIdentityItem {

    private Long id;

    private Long userId;

    private String provider;

    private String externalUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
