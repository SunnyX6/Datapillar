package com.sunny.datapillar.studio.module.workflow.service;

import java.util.List;

import com.sunny.datapillar.studio.module.workflow.dto.JobComponentDto;

/**
 * 任务Component服务
 * 提供任务Component业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface JobComponentService {

    /**
     * 查询所有可用组件
     */
    List<JobComponentDto.Response> getAllComponents();

    /**
     * 根据 code 查询组件
     */
    JobComponentDto.Response getComponentByCode(String code);

    /**
     * 根据类型查询组件
     */
    List<JobComponentDto.Response> getComponentsByType(String type);
}
