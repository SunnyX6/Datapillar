package com.sunny.job.worker.domain.entity;

/**
 * 任务组件定义实体
 * <p>
 * Worker 查询组件定义时使用，用于根据 job_type 获取 component_code
 *
 * @author SunnyX6
 * @date 2025-12-16
 */
public class JobComponent {

    private Long id;
    private String componentCode;
    private String componentName;
    private String componentType;
    private String jobParams;
    private Integer status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getComponentCode() {
        return componentCode;
    }

    public void setComponentCode(String componentCode) {
        this.componentCode = componentCode;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public String getJobParams() {
        return jobParams;
    }

    public void setJobParams(String jobParams) {
        this.jobParams = jobParams;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
