package com.sunny.datapillar.auth.dto.auth.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthTokenInfo")
public class TokenInfoResponse {

    private Long remainingSeconds;

    private Long expirationTime;

    private Long issuedAt;

    private Long userId;

    private Long tenantId;

    private String username;
}
