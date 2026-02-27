package com.sunny.datapillar.studio.dto.user.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "UserResponse")
public class UserResponse {

    private Long id;

    private Long tenantId;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private Integer level;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
