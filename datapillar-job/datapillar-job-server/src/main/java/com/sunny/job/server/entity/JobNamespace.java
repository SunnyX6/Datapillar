package com.sunny.job.server.entity;

import com.baomidou.mybatisplus.annotation.*;

/**
 * 命名空间实体
 * <p>
 * 多租户隔离
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@TableName("job_namespace")
public class JobNamespace {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String namespaceName;

    private String namespaceCode;

    private String description;

    @TableLogic
    private Integer isDeleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }

    public String getNamespaceCode() {
        return namespaceCode;
    }

    public void setNamespaceCode(String namespaceCode) {
        this.namespaceCode = namespaceCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
}
