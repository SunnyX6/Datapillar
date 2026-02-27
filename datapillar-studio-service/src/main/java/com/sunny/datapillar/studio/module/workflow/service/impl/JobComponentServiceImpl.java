package com.sunny.datapillar.studio.module.workflow.service.impl;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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
    public List<JobComponentResponse> getAllComponents() {
        List<JobComponent> components = componentMapper.selectAllComponents();
        return components.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public JobComponentResponse getComponentByCode(String code) {
        JobComponent component = componentMapper.selectByCode(code);
        if (component == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("组件不存在: code=%s", code);
        }
        return toResponse(component);
    }

    @Override
    public List<JobComponentResponse> getComponentsByType(String type) {
        List<JobComponent> components = componentMapper.selectByType(type);
        return components.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private JobComponentResponse toResponse(JobComponent entity) {
        JobComponentResponse response = new JobComponentResponse();
        BeanUtils.copyProperties(entity, response);
        return response;
    }
}
