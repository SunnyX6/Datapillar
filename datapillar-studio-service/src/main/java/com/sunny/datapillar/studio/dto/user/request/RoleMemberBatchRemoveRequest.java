package com.sunny.datapillar.studio.dto.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "RoleMemberBatchRemove")
public class RoleMemberBatchRemoveRequest {

    @NotEmpty(message = "成员ID列表不能为空")
    private List<@NotNull(message = "成员ID不能为空") Long> userIds;
}
