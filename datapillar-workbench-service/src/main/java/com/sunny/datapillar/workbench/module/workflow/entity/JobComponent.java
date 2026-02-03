package com.sunny.datapillar.workbench.module.workflow.entity;

import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;

/**
 * 组件类型实体 - 对应 job_component 表
 *
 * @author sunny
 */
@Data
@TableName(value = "job_component", autoResultMap = true)
public class JobComponent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 组件代码（如 SHELL, PYTHON, SQL）
     */
    private String componentCode;

    private String componentName;

    private String componentType;

    /**
     * 参数定义（JSON Schema）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> jobParams;

    private String description;

    private String icon;

    private String color;

    private Integer status;

    private Integer sortOrder;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
