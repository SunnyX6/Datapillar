package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.entity.JobNamespace;
import com.sunny.job.server.service.JobNamespaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 命名空间 Controller
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/namespace")
public class JobNamespaceController {

    private final JobNamespaceService namespaceService;

    public JobNamespaceController(JobNamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    /**
     * 查询命名空间列表
     */
    @GetMapping("/list")
    public ApiResponse<List<JobNamespace>> list() {
        List<JobNamespace> list = namespaceService.list();
        return ApiResponse.success(list);
    }

    /**
     * 根据 ID 查询
     */
    @GetMapping("/{id}")
    public ApiResponse<JobNamespace> getById(@PathVariable Long id) {
        JobNamespace namespace = namespaceService.getById(id);
        if (namespace == null) {
            return ApiResponse.error(404, "命名空间不存在");
        }
        return ApiResponse.success(namespace);
    }

    /**
     * 创建命名空间
     */
    @PostMapping
    public ApiResponse<JobNamespace> create(@RequestBody JobNamespace namespace) {
        // 检查 code 是否已存在
        long count = namespaceService.count(
                new LambdaQueryWrapper<JobNamespace>()
                        .eq(JobNamespace::getNamespaceCode, namespace.getNamespaceCode())
        );
        if (count > 0) {
            return ApiResponse.error(400, "命名空间编码已存在");
        }

        namespaceService.save(namespace);
        return ApiResponse.success(namespace);
    }

    /**
     * 更新命名空间
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody JobNamespace namespace) {
        namespace.setId(id);
        namespaceService.updateById(namespace);
        return ApiResponse.success();
    }

    /**
     * 删除命名空间
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        namespaceService.removeById(id);
        return ApiResponse.success();
    }
}
