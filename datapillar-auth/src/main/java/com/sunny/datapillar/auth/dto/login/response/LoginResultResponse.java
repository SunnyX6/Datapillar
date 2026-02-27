package com.sunny.datapillar.auth.dto.login.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AuthLoginResult")
public class LoginResultResponse {

    private String loginStage;

    private List<TenantOptionItem> tenants;

    private Long userId;

    private String username;

    private String email;

    private List<RoleItem> roles;

    private List<MenuItem> menus;
}
