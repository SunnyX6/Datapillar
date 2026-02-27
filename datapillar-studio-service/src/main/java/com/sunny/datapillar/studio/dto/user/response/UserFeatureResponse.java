package com.sunny.datapillar.studio.dto.user.response;

import com.sunny.datapillar.studio.dto.tenant.response.FeatureTreeNodeItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "UserFeatureResponse")
public class UserFeatureResponse {

    private List<FeatureTreeNodeItem> features;
}
