package com.sunny.datapillar.studio.dto.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "RoleUpdate")
public class RoleUpdateRequest {

    @Size(max = 64, message = "角色名称长度不能超过64个字符")
    private String name;

    @Size(max = 255, message = "角色描述长度不能超过255个字符")
    private String description;

    @Size(max = 16, message = "角色类型长度不能超过16个字符")
    private String type;
}
