package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.core.enums.WorkflowStatus;
import com.sunny.job.core.message.WorkflowBroadcast;
import com.sunny.job.core.message.WorkflowBroadcast.DependencyInfo;
import com.sunny.job.core.message.WorkflowBroadcast.OfflinePayload;
import com.sunny.job.core.message.WorkflowBroadcast.TriggerPayload;
import com.sunny.job.server.broadcast.WorkflowBroadcaster;
import com.sunny.job.server.dto.Dependency;
import com.sunny.job.server.dto.Job;
import com.sunny.job.server.entity.JobDependency;
import com.sunny.job.server.entity.JobInfo;
import com.sunny.job.server.entity.JobWorkflow;
import com.sunny.job.server.mapper.JobDependencyMapper;
import com.sunny.job.server.mapper.JobInfoMapper;
import com.sunny.job.server.mapper.JobWorkflowMapper;
import com.sunny.job.server.service.JobWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工作流定义 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobWorkflowServiceImpl extends ServiceImpl<JobWorkflowMapper, JobWorkflow>
        implements JobWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(JobWorkflowServiceImpl.class);

    private final JobInfoMapper jobInfoMapper;
    private final JobDependencyMapper jobDependencyMapper;
    private final WorkflowBroadcaster workflowBroadcaster;

    public JobWorkflowServiceImpl(JobInfoMapper jobInfoMapper,
                                   JobDependencyMapper jobDependencyMapper,
                                   WorkflowBroadcaster workflowBroadcaster) {
        this.jobInfoMapper = jobInfoMapper;
        this.jobDependencyMapper = jobDependencyMapper;
        this.workflowBroadcaster = workflowBroadcaster;
    }

    // ==================== 工作流操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWorkflow(Long namespaceId, String workflowName, Integer triggerType,
                               String triggerValue, String description) {
        JobWorkflow workflow = new JobWorkflow();
        workflow.setNamespaceId(namespaceId);
        workflow.setWorkflowName(workflowName);
        workflow.setTriggerType(triggerType);
        workflow.setTriggerValue(triggerValue);
        workflow.setDescription(description);
        workflow.setStatus(WorkflowStatus.DRAFT.getCode());
        save(workflow);

        log.info("创建工作流: workflowId={}, name={}", workflow.getId(), workflowName);
        return workflow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWorkflow(Long workflowId, String workflowName, Integer triggerType,
                               String triggerValue, Integer timeoutSeconds, Integer maxRetryTimes,
                               Integer priority, String description) {
        JobWorkflow workflow = getById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        if (workflowName != null) workflow.setWorkflowName(workflowName);
        if (triggerType != null) workflow.setTriggerType(triggerType);
        if (triggerValue != null) workflow.setTriggerValue(triggerValue);
        if (timeoutSeconds != null) workflow.setTimeoutSeconds(timeoutSeconds);
        if (maxRetryTimes != null) workflow.setMaxRetryTimes(maxRetryTimes);
        if (priority != null) workflow.setPriority(priority);
        if (description != null) workflow.setDescription(description);

        updateById(workflow);

        log.info("更新工作流: workflowId={}", workflowId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWorkflow(Long workflowId) {
        log.info("删除工作流: workflowId={}", workflowId);

        jobDependencyMapper.softDeleteByWorkflowId(workflowId);
        jobInfoMapper.softDeleteByWorkflowId(workflowId);
        removeById(workflowId);

        log.info("工作流删除成功: workflowId={}", workflowId);
    }

    // ==================== 任务节点操作 ====================

    @Override
    public List<Job> getJobs(Long workflowId) {
        List<JobInfo> jobInfoList = jobInfoMapper.selectByWorkflowId(workflowId);

        List<Job> jobs = new ArrayList<>();
        for (JobInfo info : jobInfoList) {
            Job job = new Job();
            job.setId(info.getId());
            job.setJobName(info.getJobName());
            job.setJobType(info.getJobType());
            job.setJobParams(info.getJobParams());
            job.setRouteStrategy(info.getRouteStrategy());
            job.setBlockStrategy(info.getBlockStrategy());
            job.setTimeoutSeconds(info.getTimeoutSeconds());
            job.setMaxRetryTimes(info.getMaxRetryTimes());
            job.setRetryInterval(info.getRetryInterval());
            job.setPriority(info.getPriority());
            job.setTriggerType(info.getTriggerType());
            job.setTriggerValue(info.getTriggerValue());
            job.setDescription(info.getDescription());
            job.setPositionX(info.getPositionX());
            job.setPositionY(info.getPositionY());
            jobs.add(job);
        }

        return jobs;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addJob(Long workflowId, Job job) {
        JobWorkflow workflow = getById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        JobInfo info = new JobInfo();
        info.setWorkflowId(workflowId);
        info.setNamespaceId(workflow.getNamespaceId());
        info.setJobName(job.getJobName());
        info.setJobType(job.getJobType());
        info.setJobParams(job.getJobParams());
        info.setRouteStrategy(job.getRouteStrategy());
        info.setBlockStrategy(job.getBlockStrategy());
        info.setTimeoutSeconds(job.getTimeoutSeconds());
        info.setMaxRetryTimes(job.getMaxRetryTimes());
        info.setRetryInterval(job.getRetryInterval());
        info.setPriority(job.getPriority());
        info.setTriggerType(job.getTriggerType());
        info.setTriggerValue(job.getTriggerValue());
        info.setDescription(job.getDescription());
        info.setPositionX(job.getPositionX());
        info.setPositionY(job.getPositionY());

        jobInfoMapper.insert(info);

        log.info("添加任务: workflowId={}, jobId={}, jobName={}", workflowId, info.getId(), job.getJobName());
        return info.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateJob(Long workflowId, Long jobId, Job job) {
        JobInfo existing = jobInfoMapper.selectByIdAndWorkflowId(jobId, workflowId);
        if (existing == null) {
            throw new IllegalArgumentException("任务不存在: " + jobId);
        }

        existing.setJobName(job.getJobName());
        existing.setJobType(job.getJobType());
        existing.setJobParams(job.getJobParams());
        existing.setRouteStrategy(job.getRouteStrategy());
        existing.setBlockStrategy(job.getBlockStrategy());
        existing.setTimeoutSeconds(job.getTimeoutSeconds());
        existing.setMaxRetryTimes(job.getMaxRetryTimes());
        existing.setRetryInterval(job.getRetryInterval());
        existing.setPriority(job.getPriority());
        existing.setTriggerType(job.getTriggerType());
        existing.setTriggerValue(job.getTriggerValue());
        existing.setDescription(job.getDescription());

        jobInfoMapper.updateJob(existing);

        log.info("更新任务: workflowId={}, jobId={}", workflowId, jobId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteJob(Long workflowId, Long jobId) {
        jobDependencyMapper.deleteByJobId(workflowId, jobId);
        jobInfoMapper.deleteByIdAndWorkflowId(jobId, workflowId);

        log.info("删除任务: workflowId={}, jobId={}", workflowId, jobId);
    }

    // ==================== 依赖关系操作 ====================

    @Override
    public List<Dependency> getDependencies(Long workflowId) {
        List<JobDependency> dependencyList = jobDependencyMapper.selectByWorkflowId(workflowId);

        List<Dependency> dependencies = new ArrayList<>();
        for (JobDependency dep : dependencyList) {
            Dependency d = new Dependency();
            d.setJobId(dep.getJobId());
            d.setParentJobId(dep.getParentJobId());
            dependencies.add(d);
        }

        return dependencies;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addDependency(Long workflowId, Long jobId, Long parentJobId) {
        long count = jobDependencyMapper.countDependency(workflowId, jobId, parentJobId);
        if (count > 0) {
            throw new IllegalArgumentException("依赖关系已存在");
        }

        JobDependency dep = new JobDependency();
        dep.setWorkflowId(workflowId);
        dep.setJobId(jobId);
        dep.setParentJobId(parentJobId);
        jobDependencyMapper.insert(dep);

        log.info("添加依赖: workflowId={}, jobId={}, parentJobId={}", workflowId, jobId, parentJobId);
        return dep.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDependency(Long workflowId, Long jobId, Long parentJobId) {
        jobDependencyMapper.deleteDependency(workflowId, jobId, parentJobId);

        log.info("删除依赖: workflowId={}, jobId={}, parentJobId={}", workflowId, jobId, parentJobId);
    }

    // ==================== 布局操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLayout(Long workflowId, Map<Long, double[]> positions) {
        for (Map.Entry<Long, double[]> entry : positions.entrySet()) {
            Long jobId = entry.getKey();
            double[] pos = entry.getValue();
            jobInfoMapper.updatePosition(jobId, workflowId, pos[0], pos[1]);
        }

        log.info("保存布局: workflowId={}, jobCount={}", workflowId, positions.size());
    }

    // ==================== 工作流状态操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void online(Long workflowId) {
        log.info("上线工作流: workflowId={}", workflowId);

        JobWorkflow workflow = getById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        WorkflowStatus currentStatus = WorkflowStatus.of(workflow.getStatus());
        if (!currentStatus.canOnline()) {
            throw new IllegalStateException("工作流当前状态不允许上线: " + currentStatus.getDesc());
        }

        List<Long> jobIds = jobInfoMapper.selectJobIdsByWorkflowId(workflowId);
        if (jobIds.isEmpty()) {
            throw new IllegalStateException("工作流下没有可用任务: " + workflowId);
        }

        List<JobDependency> dependencies = jobDependencyMapper.selectByWorkflowId(workflowId);

        List<DependencyInfo> depList = new ArrayList<>();
        for (JobDependency dep : dependencies) {
            depList.add(new DependencyInfo(dep.getJobId(), dep.getParentJobId()));
        }

        TriggerPayload payload = new TriggerPayload(
                workflowId,
                workflow.getNamespaceId(),
                jobIds,
                depList
        );
        WorkflowBroadcast event = WorkflowBroadcast.online(payload);

        workflow.setStatus(WorkflowStatus.ONLINE.getCode());
        updateById(workflow);

        workflowBroadcaster.broadcast(event);

        log.info("工作流上线请求已提交: workflowId={}, eventId={}, jobCount={}",
                workflowId, event.getEventId(), jobIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long workflowId) {
        log.info("下线工作流: workflowId={}", workflowId);

        JobWorkflow workflow = getById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        workflow.setStatus(WorkflowStatus.OFFLINE.getCode());
        updateById(workflow);

        OfflinePayload payload = new OfflinePayload(workflowId);
        WorkflowBroadcast event = WorkflowBroadcast.offline(payload);

        workflowBroadcaster.broadcast(event);

        log.info("工作流下线成功: workflowId={}", workflowId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void trigger(Long workflowId) {
        log.info("手动触发工作流: workflowId={}", workflowId);

        JobWorkflow workflow = getById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        List<Long> jobIds = jobInfoMapper.selectJobIdsByWorkflowId(workflowId);
        if (jobIds.isEmpty()) {
            throw new IllegalStateException("工作流下没有可用任务: " + workflowId);
        }

        List<JobDependency> dependencies = jobDependencyMapper.selectByWorkflowId(workflowId);

        List<DependencyInfo> depList = new ArrayList<>();
        for (JobDependency dep : dependencies) {
            depList.add(new DependencyInfo(dep.getJobId(), dep.getParentJobId()));
        }

        TriggerPayload payload = new TriggerPayload(
                workflowId,
                workflow.getNamespaceId(),
                jobIds,
                depList
        );
        WorkflowBroadcast event = WorkflowBroadcast.manualTrigger(payload);

        workflowBroadcaster.broadcast(event);

        log.info("工作流手动触发请求已提交: workflowId={}, eventId={}, jobCount={}",
                workflowId, event.getEventId(), jobIds.size());
    }
}
