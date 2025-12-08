package com.sunny.job.admin.scheduler.complete;

import com.sunny.job.admin.dag.WorkflowExecutor;
import com.sunny.job.admin.dag.DAGEngine;
import com.sunny.job.admin.model.DatapillarJobDependency;
import com.sunny.job.admin.scheduler.conf.DatapillarJobAdminConfig;
import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobLog;
import com.sunny.job.admin.scheduler.thread.JobTriggerPoolHelper;
import com.sunny.job.admin.scheduler.trigger.TriggerTypeEnum;
import com.sunny.job.admin.util.I18nUtil;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.context.DatapillarJobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.text.MessageFormat;
import java.util.List;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Component
public class DatapillarJobCompleter {
    private static Logger logger = LoggerFactory.getLogger(DatapillarJobCompleter.class);

    private static WorkflowExecutor workflowExecutor;
    private static DAGEngine dagEngine;

    @Resource
    public void setWorkflowExecutor(WorkflowExecutor workflowExecutor) {
        DatapillarJobCompleter.workflowExecutor = workflowExecutor;
    }

    @Resource
    public void setDagEngine(DAGEngine dagEngine) {
        DatapillarJobCompleter.dagEngine = dagEngine;
    }

    /**
     * common fresh handle entrance (limit only once)
     *
     * @param datapillarJobLog
     * @return
     */
    public static int updateHandleInfoAndFinish(DatapillarJobLog datapillarJobLog) {

        // finish
        finishJob(datapillarJobLog);

        // 完成 Future（兜底机制：monitor 超时标记失败时也需要完成 Future）
        com.sunny.job.admin.scheduler.thread.JobCallbackFutureHolder.completeFuture(datapillarJobLog.getJobId(), datapillarJobLog);

        // text最大64kb 避免长度过长
        if (datapillarJobLog.getHandleMsg().length() > 15000) {
            datapillarJobLog.setHandleMsg( datapillarJobLog.getHandleMsg().substring(0, 15000) );
        }

        // fresh handle
        return DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobLogMapper().updateHandleInfo(datapillarJobLog);
    }


    /**
     * do somethind to finish job
     */
    private static void finishJob(DatapillarJobLog datapillarJobLog){

        // 1、handle DAG dependency trigger (replaced native child job feature)
        String triggerDependencyMsg = null;
        if (workflowExecutor != null && dagEngine != null) {
            try {
                // 从jobInfo中获取workflow_id
                DatapillarJobInfo jobInfo = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobInfoMapper().loadById(datapillarJobLog.getJobId());
                Long workflowId = (jobInfo != null) ? jobInfo.getWorkflowId() : null;

                if (workflowId != null && workflowId > 0) {
                    int jobId = datapillarJobLog.getJobId();

                    // 更新任务状态
                    String taskStatus = (DatapillarJobContext.HANDLE_CODE_SUCCESS == datapillarJobLog.getHandleCode()) ? "COMPLETED" : "FAILED";

                    // 如果任务失败，记录失败原因
                    if ("FAILED".equals(taskStatus)) {
                        String failureReason = datapillarJobLog.getHandleMsg();
                        workflowExecutor.updateTaskStatus(workflowId, jobId, taskStatus, failureReason);
                    } else {
                        workflowExecutor.updateTaskStatus(workflowId, jobId, taskStatus);
                    }

                    // 获取所有依赖当前任务的下游任务
                    List<DatapillarJobDependency> dependents = dagEngine.getDependents(jobId);

                    if (dependents != null && !dependents.isEmpty()) {
                        triggerDependencyMsg = "<br><br><span style=\"color:#00c0ef;\" > >>>>>>>>>>>DAG依赖触发<<<<<<<<<<< </span><br>";

                        for (DatapillarJobDependency dependency : dependents) {
                            int dependentJobId = dependency.getToJobId();

                            // 标记依赖完成(原子操作)
                            workflowExecutor.markDependencyCompleted(workflowId, dependentJobId, jobId);

                            // 检查下游任务的所有依赖是否都已满足
                            if (dagEngine.allDependenciesSatisfied(dependentJobId, workflowId)) {
                                // 先尝试CAS锁定任务状态,避免并发重复触发
                                boolean casSuccess = workflowExecutor.tryTriggerTask(workflowId, dependentJobId);
                                if (casSuccess) {
                                    try {
                                        // CAS成功,通过DAG触发下游任务
                                        JobTriggerPoolHelper.trigger(dependentJobId, TriggerTypeEnum.DAG, -1, null, null, null);

                                        triggerDependencyMsg += MessageFormat.format("任务 {0} 依赖已满足，触发执行<br>", dependentJobId);
                                        logger.info("DAG依赖触发: workflowId={}, 完成任务={}, 触发任务={}", workflowId, jobId, dependentJobId);
                                    } catch (Exception e) {
                                        // Trigger失败,回滚状态为PENDING
                                        workflowExecutor.updateTaskStatus(workflowId, dependentJobId, "PENDING");
                                        triggerDependencyMsg += MessageFormat.format("任务 {0} 触发失败: {1}<br>", dependentJobId, e.getMessage());
                                        logger.error("DAG任务触发失败,已回滚: workflowId={}, 任务={}", workflowId, dependentJobId, e);
                                    }
                                } else {
                                    // CAS失败,任务已被其他线程触发
                                    triggerDependencyMsg += MessageFormat.format("任务 {0} 已被并发触发，跳过<br>", dependentJobId);
                                    logger.debug("DAG任务已被并发触发: workflowId={}, 任务={}", workflowId, dependentJobId);
                                }
                            } else {
                                triggerDependencyMsg += MessageFormat.format("任务 {0} 依赖未满足，等待其他依赖完成<br>", dependentJobId);
                                logger.debug("DAG依赖未满足: workflowId={}, 等待任务={}", workflowId, dependentJobId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("处理DAG依赖触发失败", e);
                triggerDependencyMsg = "<br><span style=\"color:red;\" >DAG依赖触发异常: " + e.getMessage() + "</span><br>";
            }
        }

        if (triggerDependencyMsg != null) {
            datapillarJobLog.setHandleMsg( datapillarJobLog.getHandleMsg() + triggerDependencyMsg );
        }

        // 3、fix_delay trigger next
        // on the way

    }

}
