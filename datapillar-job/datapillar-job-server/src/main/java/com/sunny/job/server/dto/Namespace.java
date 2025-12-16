package com.sunny.job.server.dto;

import com.sunny.job.server.entity.JobNamespace;

/**
 * 命名空间 DTO
 *
 * @author SunnyX6
 * @date 2025-12-16
 */
public class Namespace {

    private Long id;

    private String namespaceCode;

    private String namespaceName;

    private String description;

    public static Namespace from(JobNamespace entity) {
        Namespace dto = new Namespace();
        dto.setId(entity.getId());
        dto.setNamespaceCode(entity.getNamespaceCode());
        dto.setNamespaceName(entity.getNamespaceName());
        dto.setDescription(entity.getDescription());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNamespaceCode() {
        return namespaceCode;
    }

    public void setNamespaceCode(String namespaceCode) {
        this.namespaceCode = namespaceCode;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
