package com.sunny.datapillar.studio.module.workflow.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 任务依赖关系实体 - 对应 job_dependency 表
 *
 * @author sunny
 */
@Data
@TableName("job_dependency")
public class JobDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    /**
     * 当前任务 ID
     */
    private Long jobId;

    /**
     * 上游任务 ID（父节点）
     */
    private Long parentJobId;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
