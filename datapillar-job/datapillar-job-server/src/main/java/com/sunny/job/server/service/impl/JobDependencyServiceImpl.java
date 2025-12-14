package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.core.dag.DagEdge;
import com.sunny.job.core.dag.DagGraph;
import com.sunny.job.core.dag.DagValidationException;
import com.sunny.job.server.entity.JobDependency;
import com.sunny.job.server.mapper.JobDependencyMapper;
import com.sunny.job.server.service.JobDependencyService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 任务依赖 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobDependencyServiceImpl extends ServiceImpl<JobDependencyMapper, JobDependency>
        implements JobDependencyService {

    @Override
    public void validateNoCycle(Long workflowId, List<JobDependency> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }

        // 收集所有节点 ID
        Set<Long> nodeIds = new HashSet<>();
        List<DagEdge> edges = new ArrayList<>();

        for (JobDependency dep : dependencies) {
            nodeIds.add(dep.getJobId());
            nodeIds.add(dep.getParentJobId());
            edges.add(new DagEdge(dep.getParentJobId(), dep.getJobId()));
        }

        // 使用 DagGraph 验证无环
        try {
            DagGraph.from(nodeIds, edges);
        } catch (DagValidationException e) {
            throw new IllegalArgumentException("工作流 " + workflowId + " 存在循环依赖: " + e.getMessage());
        }
    }

    @Override
    public void validateSingleDependency(JobDependency dependency) {
        // 查询当前工作流的所有依赖
        List<JobDependency> existingDeps = list(
                new LambdaQueryWrapper<JobDependency>()
                        .eq(JobDependency::getWorkflowId, dependency.getWorkflowId())
        );

        // 添加新依赖
        List<JobDependency> allDeps = new ArrayList<>(existingDeps);
        allDeps.add(dependency);

        // 验证是否产生环
        validateNoCycle(dependency.getWorkflowId(), allDeps);
    }
}
