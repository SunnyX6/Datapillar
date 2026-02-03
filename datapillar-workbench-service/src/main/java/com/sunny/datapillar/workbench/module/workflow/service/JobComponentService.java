package com.sunny.datapillar.workbench.module.workflow.service;

import java.util.List;

import com.sunny.datapillar.workbench.module.workflow.dto.JobComponentDto;

/**
 * 组件服务接口
 *
 * @author sunny
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
