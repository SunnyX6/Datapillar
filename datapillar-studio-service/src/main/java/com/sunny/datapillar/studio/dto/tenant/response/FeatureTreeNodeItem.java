package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "FeatureObjectTreeNode")
public class FeatureTreeNodeItem {

    private Long id;

    private Long parentId;

    private String type;

    private String name;

    private String path;

    private String location;

    private Integer sort;

    private Integer status;

    private Long categoryId;

    private String categoryName;

    private List<FeatureTreeNodeItem> children;
}
