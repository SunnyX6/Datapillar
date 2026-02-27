package com.sunny.datapillar.studio.dto.user.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "RoleMembersResponse")
public class RoleMembersResponse {

    private Long roleId;

    private String roleName;

    private String roleType;

    private Integer roleLevel;

    private Integer roleStatus;

    private Long memberCount;

    private List<RoleMemberItem> members;
}
