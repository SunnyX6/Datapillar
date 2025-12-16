package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.common.ParamValidator;
import com.sunny.job.server.dto.Namespace;
import com.sunny.job.server.entity.JobNamespace;
import com.sunny.job.server.service.JobNamespaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
    public ApiResponse<List<Namespace>> list() {
        List<JobNamespace> list = namespaceService.list();
        List<Namespace> voList = list.stream()
                .map(Namespace::from)
                .collect(Collectors.toList());
        return ApiResponse.success(voList);
    }

    /**
     * 根据 ID 查询
     */
    @GetMapping("/{id}")
    public ApiResponse<Namespace> getById(@PathVariable Long id) {
        JobNamespace namespace = namespaceService.getById(id);
        if (namespace == null) {
            return ApiResponse.error(404, "命名空间不存在");
        }
        return ApiResponse.success(Namespace.from(namespace));
    }

    /**
     * 创建命名空间
     */
    @PostMapping
    public ApiResponse<Long> create(@RequestParam String namespaceCode,
                                    @RequestParam String namespaceName,
                                    @RequestParam(required = false) String description) {
        ParamValidator.requireNotBlank(namespaceCode, "namespaceCode");
        ParamValidator.requireNotBlank(namespaceName, "namespaceName");

        long count = namespaceService.count(
                new LambdaQueryWrapper<JobNamespace>()
                        .eq(JobNamespace::getNamespaceCode, namespaceCode)
        );
        if (count > 0) {
            return ApiResponse.error(400, "命名空间编码已存在");
        }

        JobNamespace namespace = new JobNamespace();
        namespace.setNamespaceCode(namespaceCode);
        namespace.setNamespaceName(namespaceName);
        namespace.setDescription(description);
        namespaceService.save(namespace);

        return ApiResponse.success(namespace.getId());
    }

    /**
     * 更新命名空间
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @RequestParam(required = false) String namespaceName,
                                    @RequestParam(required = false) String description) {
        JobNamespace namespace = namespaceService.getById(id);
        if (namespace == null) {
            return ApiResponse.error(404, "命名空间不存在");
        }

        if (namespaceName != null) {
            namespace.setNamespaceName(namespaceName);
        }
        if (description != null) {
            namespace.setDescription(description);
        }
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
