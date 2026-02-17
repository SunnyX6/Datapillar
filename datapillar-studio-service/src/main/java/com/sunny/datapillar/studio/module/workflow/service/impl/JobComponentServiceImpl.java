package com.sunny.datapillar.studio.module.workflow.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.sunny.datapillar.studio.module.workflow.dto.JobComponentDto;
import com.sunny.datapillar.studio.module.workflow.entity.JobComponent;
import com.sunny.datapillar.studio.module.workflow.mapper.JobComponentMapper;
import com.sunny.datapillar.studio.module.workflow.service.JobComponentService;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.sunny.datapillar.common.exception.NotFoundException;

/**
 * 任务Component服务实现
 * 实现任务Component业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobComponentServiceImpl implements JobComponentService {

    private final JobComponentMapper componentMapper;

    @Override
    public List<JobComponentDto.Response> getAllComponents() {
        List<JobComponent> components = componentMapper.selectAllComponents();
        return components.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public JobComponentDto.Response getComponentByCode(String code) {
        JobComponent component = componentMapper.selectByCode(code);
        if (component == null) {
            throw new NotFoundException("组件不存在: code=%s", code);
        }
        return toResponse(component);
    }

    @Override
    public List<JobComponentDto.Response> getComponentsByType(String type) {
        List<JobComponent> components = componentMapper.selectByType(type);
        return components.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private JobComponentDto.Response toResponse(JobComponent entity) {
        JobComponentDto.Response response = new JobComponentDto.Response();
        BeanUtils.copyProperties(entity, response);
        return response;
    }
}
