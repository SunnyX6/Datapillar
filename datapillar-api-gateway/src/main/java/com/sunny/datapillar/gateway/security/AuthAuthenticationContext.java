package com.sunny.datapillar.gateway.security;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Auth-owned authentication context payload consumed by gateway. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthAuthenticationContext {

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
