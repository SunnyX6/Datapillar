package com.sunny.datapillar.studio.module.user.dto;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import java.util.List;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户功能数据传输对象
 * 定义用户功能数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class UserFeatureDto {

    @Data
    @Schema(name = "UserFeatureResponse")
    public static class Response {
        private List<FeatureObjectDto.TreeNode> features;
    }
}
