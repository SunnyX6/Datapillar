package com.sunny.job.admin.dag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sunny.job.admin.mapper.DatapillarJobDependencyMapper;
import com.sunny.job.admin.mapper.DatapillarJobInfoMapper;
import com.sunny.job.admin.mapper.DatapillarJobWorkflowMapper;
import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobWorkflow;

import jakarta.annotation.Resource;

/**
 * 工作流执行器
 * 负责工作流的 DAG 执行、任务状态管理、依赖检查等
 *
 * @author datapillar-job-admin
 * @date 2025-11-06
 */
@Component
public class WorkflowExecutor {
    private static Logger logger = LoggerFactory.getLogger(WorkflowExecutor.class);

    @Resource
    private DatapillarJobWorkflowMapper workflowMapper;

    @Resource
    private DatapillarJobInfoMapper jobInfoMapper;

    @Resource
    private DatapillarJobDependencyMapper dependencyMapper;

    @Resource
    private DAGEngine dagEngine;

    /**
     * 启动工作流执行
     * 智能处理不同状态:
     * - DRAFT: 首次启动
     * - FAILED/COMPLETED/STOPPED: 自动清理后重新启动
     * - RUNNING: 不允许启动(避免并发)
     */
    public void startWorkflow(long workflowId) {
        // 获取workflow
        DatapillarJobWorkflow workflow = workflowMapper.loadById(workflowId);
        if (workflow == null) {
            throw new RuntimeException("工作流不存在: " + workflowId);
        }

        // 检查工作流状态
        String currentStatus = workflow.getStatus();

        // 如果正在运行,不允许重复启动
        if ("RUNNING".equals(currentStatus)) {
            throw new RuntimeException("工作流正在运行中,请等待执行完成");
        }

        try {
            // 进行拓扑排序,检测循环依赖
            dagEngine.topologicalSortForWorkflow(workflowId);
        } catch (CycleDetectedException e) {
            logger.error("启动工作流失败,检测到循环依赖: {}", e.getMessage());
            throw new RuntimeException("无法启动工作流,存在循环依赖: " + e.getMessage());
        }

        // 获取workflow的所有job
        List<com.sunny.job.admin.model.DatapillarJobInfo> jobs = jobInfoMapper.findByWorkflowId(workflowId);
        if (jobs.isEmpty()) {
            throw new RuntimeException("工作流没有任务");
        }

        // 重置所有任务状态为PENDING（不删除重建，只UPDATE）
        if (Arrays.asList("COMPLETED", "FAILED", "STOPPED").contains(currentStatus)) {
            logger.info("工作流状态为{},重置任务状态: workflowId={}", currentStatus, workflowId);
            resetTaskStates(workflowId);
        }

        // 更新工作流状态为RUNNING
        workflow.setStatus("RUNNING");
        workflow.setStartTime(new Date());
        workflow.setEndTime(null);  // 清空结束时间
        workflowMapper.updateStatus(workflow);

        logger.info("启动工作流成功: workflowId={}, 任务数={}", workflowId, jobs.size());
    }

    /**
     * 重新运行整个工作流
     * 智能处理不同状态:
     * - DRAFT: 首次启动
     * - FAILED/COMPLETED/STOPPED: 清理后重新启动
     * - RUNNING: 不允许重跑(避免并发)
     */
    public void rerunWorkflow(long workflowId) {
        // 获取workflow
        DatapillarJobWorkflow workflow = workflowMapper.loadById(workflowId);
        if (workflow == null) {
            throw new RuntimeException("工作流不存在: " + workflowId);
        }

        // 检查工作流状态
        String currentStatus = workflow.getStatus();

        // 如果正在运行,不允许重跑
        if ("RUNNING".equals(currentStatus)) {
            throw new RuntimeException("工作流正在运行中,请等待执行完成");
        }

        try {
            // 进行拓扑排序,检测循环依赖
            dagEngine.topologicalSortForWorkflow(workflowId);
        } catch (CycleDetectedException e) {
            logger.error("重跑工作流失败,检测到循环依赖: {}", e.getMessage());
            throw new RuntimeException("无法重跑工作流,存在循环依赖: " + e.getMessage());
        }

        // 获取workflow的所有job
        List<com.sunny.job.admin.model.DatapillarJobInfo> jobs = jobInfoMapper.findByWorkflowId(workflowId);
        if (jobs.isEmpty()) {
            throw new RuntimeException("工作流没有任务");
        }

        // 重置所有任务状态为PENDING（不删除重建，只UPDATE）
        logger.info("重跑工作流,重置任务状态: workflowId={}", workflowId);
        resetTaskStates(workflowId);

        // 更新工作流状态为RUNNING
        workflow.setStatus("RUNNING");
        workflow.setStartTime(new Date());
        workflow.setEndTime(null);
        workflowMapper.updateStatus(workflow);

        logger.info("重新运行工作流成功: workflowId={}, 任务数={}", workflowId, jobs.size());
    }

    /**
     * 重置工作流中所有任务的状态（UPDATE，不删除）
     * 用于重跑工作流时，只更新状态，提升性能
     * 一条SQL完成，避免循环更新
     */
    private void resetTaskStates(long workflowId) {
        int count = jobInfoMapper.resetWorkflowTaskStates(workflowId);
        logger.info("工作流 {} 重置了 {} 个任务状态", workflowId, count);
    }

    /**
     * 获取可以执行的任务列表（依赖已满足且状态为PENDING）
     */
    public List<Integer> getReadyTasks(long workflowId) {
        List<Integer> readyTasks = new ArrayList<>();
        List<DatapillarJobInfo> pendingTasks = jobInfoMapper.findPendingTasks(workflowId);

        for (DatapillarJobInfo jobInfo : pendingTasks) {
            int jobId = jobInfo.getId();

            // 检查所有依赖是否已满足
            if (dagEngine.allDependenciesSatisfied(jobId, workflowId)) {
                readyTasks.add(jobId);
            }
        }

        logger.debug("工作流 {} 当前可执行任务: {}", workflowId, readyTasks);
        return readyTasks;
    }

    /**
     * 更新任务状态
     */
    public void updateTaskStatus(long workflowId, int jobId, String status) {
        updateTaskStatus(workflowId, jobId, status, null);
    }

    /**
     * 更新任务状态（带失败原因）
     */
    public void updateTaskStatus(long workflowId, int jobId, String status, String failureReason) {
        DatapillarJobInfo jobInfo = jobInfoMapper.loadByWorkflowAndJob(workflowId, jobId);

        if (jobInfo == null) {
            logger.error("任务不存在: workflowId={}, jobId={}", workflowId, jobId);
            return;
        }

        jobInfo.setStatus(status);
        // update_time 由数据库自动更新

        // 如果是开始执行,记录开始时间
        if ("RUNNING".equals(status)) {
            jobInfo.setStartTime(new Date());  // start_time 由代码设置
        }

        // 如果是完成状态,记录结束时间
        if (Arrays.asList("COMPLETED", "FAILED", "SKIPPED").contains(status)) {
            jobInfo.setEndTime(new Date());  // end_time 由代码设置
        }

        jobInfoMapper.updateStatus(jobInfo);
        logger.info("更新任务状态: workflowId={}, jobId={}, status={}", workflowId, jobId, status);

        // 任务完成后检查工作流是否完成
        if (Arrays.asList("COMPLETED", "FAILED", "SKIPPED").contains(status)) {
            checkWorkflowCompletion(workflowId);
        }
    }

    /**
     * 检查工作流是否已完成
     */
    private void checkWorkflowCompletion(long workflowId) {
        List<DatapillarJobInfo> allTasks = jobInfoMapper.findByWorkflowId(workflowId);

        boolean allFinished = true;
        boolean hasFailure = false;

        for (DatapillarJobInfo jobInfo : allTasks) {
            String status = jobInfo.getStatus();

            if ("PENDING".equals(status) || "RUNNING".equals(status)) {
                allFinished = false;
                break;
            }

            if ("FAILED".equals(status)) {
                hasFailure = true;
            }
        }

        if (allFinished) {
            DatapillarJobWorkflow workflow = workflowMapper.loadById(workflowId);

            if (hasFailure) {
                workflow.setStatus("FAILED");
                logger.warn("工作流 {} 执行失败", workflowId);
            } else {
                workflow.setStatus("COMPLETED");
                logger.info("工作流 {} 执行成功", workflowId);
            }

            workflow.setEndTime(new Date());
            // 优化: 合并为一次更新,避免重复UPDATE
            workflowMapper.updateStatus(workflow);
        }
    }

    /**
     * 标记任务依赖已完成(并发安全版本)
     * 使用数据库原子操作,避免并发更新丢失
     */
    public void markDependencyCompleted(long workflowId, int jobId, int dependencyJobId) {
        // 使用数据库JSON函数原子性添加依赖
        int updateCount = jobInfoMapper.addDependencyCompletedAtomic(workflowId, jobId, dependencyJobId);

        if (updateCount > 0) {
            logger.debug("标记依赖完成: workflowId={}, jobId={}, dependencyJobId={}",
                    workflowId, jobId, dependencyJobId);
        } else {
            logger.debug("依赖已存在或任务不存在: workflowId={}, jobId={}, dependencyJobId={}",
                    workflowId, jobId, dependencyJobId);
        }
    }

    /**
     * 尝试触发任务(并发安全)
     * 使用CAS方式将状态从PENDING更新为RUNNING
     *
     * @return true表示触发成功, false表示任务已被其他线程触发
     */
    public boolean tryTriggerTask(long workflowId, int jobId) {
        // 使用CAS方式更新状态: PENDING -> RUNNING
        int updateCount = jobInfoMapper.updateStatusWithCAS(
                workflowId,
                jobId,
                "PENDING", // oldStatus
                "RUNNING", // newStatus
                new Date() // startTime
        );

        if (updateCount > 0) {
            logger.debug("CAS触发任务成功: workflowId={}, jobId={}", workflowId, jobId);
            return true;
        } else {
            logger.debug("CAS触发任务失败(已触发): workflowId={}, jobId={}", workflowId, jobId);
            return false;
        }
    }

    /**
     * 获取工作流状态
     */
    public DatapillarJobWorkflow getWorkflow(long workflowId) {
        return workflowMapper.loadById(workflowId);
    }

    /**
     * 获取工作流中所有任务状态
     */
    public List<DatapillarJobInfo> getWorkflowTasks(long workflowId) {
        return jobInfoMapper.findByWorkflowId(workflowId);
    }

    /**
     * 获取工作流中单个任务状态
     */
    public DatapillarJobInfo getWorkflowTask(long workflowId, int jobId) {
        return jobInfoMapper.loadByWorkflowAndJob(workflowId, jobId);
    }

    /**
     * 停止工作流
     */
    public void stopWorkflow(long workflowId) {
        DatapillarJobWorkflow workflow = workflowMapper.loadById(workflowId);

        if (workflow == null) {
            logger.error("工作流不存在: workflowId={}", workflowId);
            return;
        }

        workflow.setStatus("STOPPED");
        workflow.setEndTime(new Date());
        workflowMapper.updateStatus(workflow);

        // 将所有未完成的任务标记为SKIPPED
        jobInfoMapper.batchUpdateStatus(workflowId, null, "SKIPPED");

        logger.info("工作流 {} 已停止", workflowId);
    }


    /**
     * 重新运行单个任务
     * 将任务设为RUNNING,配合MANUAL_SINGLE类型直接执行
     */
    public void retryTask(long workflowId, int jobId) {
        // 检查工作流是否存在
        DatapillarJobWorkflow workflow = workflowMapper.loadById(workflowId);
        if (workflow == null) {
            throw new RuntimeException("工作流不存在: " + workflowId);
        }

        // 获取任务
        DatapillarJobInfo jobInfo = jobInfoMapper.loadByWorkflowAndJob(workflowId, jobId);
        if (jobInfo == null) {
            throw new RuntimeException("任务不存在: workflowId=" + workflowId + ", jobId=" + jobId);
        }

        // 如果工作流不是RUNNING状态,需要更新为RUNNING
        if (!"RUNNING".equals(workflow.getStatus())) {
            workflow.setStatus("RUNNING");
            workflow.setStartTime(new Date());
            workflow.setEndTime(null);
            workflowMapper.updateStatus(workflow);
            logger.info("工作流状态更新为RUNNING: workflowId={}", workflowId);
        }

        // 设置为RUNNING状态,准备执行
        jobInfo.setStatus("RUNNING");
        jobInfo.setStartTime(new Date());
        jobInfo.setEndTime(null);
        jobInfoMapper.updateStatus(jobInfo);

        logger.info("手动重跑单个任务,设为RUNNING: workflowId={}, jobId={}", workflowId, jobId);
    }

    /**
     * 从某个节点开始重跑(包含该节点及所有下游节点)
     * 起始任务设为RUNNING,下游任务设为PENDING,配合MANUAL_CASCADE类型级联执行
     */
    public void rerunFromTask(long workflowId, int startJobId) {
        // 检查工作流是否存在
        DatapillarJobWorkflow workflow = workflowMapper.loadById(workflowId);
        if (workflow == null) {
            throw new RuntimeException("工作流不存在: " + workflowId);
        }

        // 获取起始任务
        DatapillarJobInfo startJobInfo = jobInfoMapper.loadByWorkflowAndJob(workflowId, startJobId);
        if (startJobInfo == null) {
            throw new RuntimeException("起始任务不存在: workflowId=" + workflowId + ", jobId=" + startJobId);
        }

        // 获取所有下游任务(递归)
        List<Integer> downstreamJobs = dagEngine.getAllDownstreamJobs(startJobId, workflowId);

        // 起始任务设为RUNNING,准备执行
        startJobInfo.setStatus("RUNNING");
        startJobInfo.setStartTime(new Date());
        startJobInfo.setEndTime(null);
        jobInfoMapper.updateStatus(startJobInfo);

        // 下游任务设为PENDING,等待级联触发
        for (Integer jobId : downstreamJobs) {
            DatapillarJobInfo jobInfo = jobInfoMapper.loadByWorkflowAndJob(workflowId, jobId);
            if (jobInfo != null) {
                jobInfo.setStatus("PENDING");
                jobInfo.setStartTime(null);
                jobInfo.setEndTime(null);
                jobInfoMapper.updateStatus(jobInfo);
            }
        }

        // 如果工作流不是RUNNING状态,需要更新为RUNNING
        if (!"RUNNING".equals(workflow.getStatus())) {
            workflow.setStatus("RUNNING");
            workflow.setStartTime(new Date());
            workflow.setEndTime(null);
            workflowMapper.updateStatus(workflow);
        }

        logger.info("从任务{}重跑,起始任务设为RUNNING,{}个下游任务设为PENDING: workflowId={}, downstream={}",
                startJobId, downstreamJobs.size(), workflowId, downstreamJobs);
    }

}
