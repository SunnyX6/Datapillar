package com.sunny.datapillar.auth.dto.login.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthRoleInfo")
public class RoleItem {

    private Long id;

    private String name;

    private String type;
}
