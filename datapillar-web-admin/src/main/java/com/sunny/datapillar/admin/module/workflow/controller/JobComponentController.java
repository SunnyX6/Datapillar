package com.sunny.datapillar.admin.module.workflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.admin.module.workflow.dto.JobComponentDto;
import com.sunny.datapillar.admin.module.workflow.service.JobComponentService;
import com.sunny.datapillar.admin.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 组件控制器
 *
 * @author sunny
 */
@Tag(name = "组件管理", description = "查询可用的任务组件")
@RestController
@RequestMapping("/components")
@RequiredArgsConstructor
public class JobComponentController {

    private final JobComponentService componentService;

    @Operation(summary = "获取所有可用组件")
    @GetMapping
    public ApiResponse<List<JobComponentDto.Response>> list() {
        List<JobComponentDto.Response> result = componentService.getAllComponents();
        return ApiResponse.ok(result);
    }

    @Operation(summary = "根据 code 获取组件信息")
    @GetMapping("/code/{code}")
    public ApiResponse<JobComponentDto.Response> getByCode(@PathVariable String code) {
        JobComponentDto.Response result = componentService.getComponentByCode(code);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "根据类型获取组件列表")
    @GetMapping("/type/{type}")
    public ApiResponse<List<JobComponentDto.Response>> getByType(@PathVariable String type) {
        List<JobComponentDto.Response> result = componentService.getComponentsByType(type);
        return ApiResponse.ok(result);
    }
}
