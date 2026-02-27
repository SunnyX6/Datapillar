package com.sunny.datapillar.auth.dto.login.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthLoginResponse")
public class LoginResponse {

    private Long userId;

    private Long tenantId;

    private String username;

    private String email;

    private List<RoleItem> roles;

    private List<MenuItem> menus;
}
