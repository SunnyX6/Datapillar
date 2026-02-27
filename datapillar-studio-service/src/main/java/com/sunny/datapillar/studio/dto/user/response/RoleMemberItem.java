package com.sunny.datapillar.studio.dto.user.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "RoleMemberItem")
public class RoleMemberItem {

    private Long userId;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private Integer userLevel;

    private Integer memberStatus;

    private LocalDateTime joinedAt;

    private LocalDateTime assignedAt;
}
