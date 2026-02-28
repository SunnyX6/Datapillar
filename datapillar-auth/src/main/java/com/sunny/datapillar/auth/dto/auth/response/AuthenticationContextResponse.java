package com.sunny.datapillar.auth.dto.auth.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthAuthenticationContext")
public class AuthenticationContextResponse {

    private Long userId;

    private Long tenantId;

    private String tenantCode;

    private String tenantName;

    private String username;

    private String email;

    private List<String> roles;

    private Boolean impersonation;

    private Long actorUserId;

    private Long actorTenantId;

    private String sessionId;

    private String tokenId;
}
