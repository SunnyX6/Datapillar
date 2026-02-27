package com.sunny.datapillar.auth.dto.auth.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthTokenResponse")
public class TokenResponse {

    private boolean valid;

    private Long userId;

    private Long tenantId;

    private String username;

    private String email;

    private String errorMessage;
}
