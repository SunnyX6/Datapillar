package com.sunny.job.admin.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sunny.job.admin.dag.WorkflowExecutor;
import com.sunny.job.admin.dto.ProjectWorkflowSummaryDTO;
import com.sunny.job.admin.dto.WorkflowNodeDTO;
import com.sunny.job.admin.enums.WorkflowNodeType;
import com.sunny.job.admin.mapper.DatapillarJobDependencyMapper;
import com.sunny.job.admin.mapper.DatapillarJobGroupMapper;
import com.sunny.job.admin.mapper.DatapillarJobInfoMapper;
import com.sunny.job.admin.mapper.DatapillarJobWorkflowMapper;
import com.sunny.job.admin.model.DatapillarJobDependency;
import com.sunny.job.admin.model.DatapillarJobGroup;
import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobWorkflow;
import com.sunny.job.admin.scheduler.thread.JobTriggerPoolHelper;
import com.sunny.job.admin.scheduler.trigger.TriggerTypeEnum;
import com.sunny.job.admin.service.DatapillarJobWorkflowService;
import com.sunny.job.admin.util.I18nUtil;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.enums.ExecutorBlockStrategyEnum;
import com.sunny.job.core.glue.GlueTypeEnum;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * datapillar-job workflow service impl
 * 负责工作流的CRUD管理和执行操作
 *
 * @author datapillar-job-admin
 * @date 2025-11-09
 */
@Service
public class DatapillarJobWorkflowServiceImpl implements DatapillarJobWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(DatapillarJobWorkflowServiceImpl.class);

    @Resource
    private DatapillarJobWorkflowMapper workflowMapper;

    @Resource
    private WorkflowExecutor workflowExecutor;

    @Resource
    private DatapillarJobInfoMapper datapillarJobInfoMapper;

    @Resource
    private DatapillarJobDependencyMapper datapillarJobDependencyMapper;

    @Resource
    private DatapillarJobGroupMapper datapillarJobGroupMapper;

    @Resource
    private com.sunny.job.admin.dag.DAGEngine dagEngine;

    // ==================== CRUD操作 ====================

    @Override
    public ReturnT<DatapillarJobWorkflow> create(DatapillarJobWorkflow workflow) {
        if (workflow == null || workflow.getName() == null || workflow.getName().trim().isEmpty()) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "工作流名称不能为空");
        }

        if (workflow.getProjectId() == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "项目ID不能为空");
        }

        // 检查重复:由于MySQL的NULL!=NULL特性,需要在代码层面检查
        DatapillarJobWorkflow existingWorkflow = workflowMapper.findByProjectIdAndFolderIdAndName(
            workflow.getProjectId(),
            workflow.getFolderId(),
            workflow.getName().trim()
        );
        if (existingWorkflow != null) {
            if (workflow.getFolderId() == null) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "该项目下已存在同名工作流(未分类)");
            } else {
                return new ReturnT<>(ReturnT.FAIL_CODE, "该文件夹下已存在同名工作流");
            }
        }

        // 设置默认值
        if (workflow.getStatus() == null) {
            workflow.setStatus("DRAFT");
        }

        if (workflow.getVersion() == null) {
            workflow.setVersion(1);
        }

        // 插入
        try {
            int result = workflowMapper.insert(workflow);
            if (result <= 0) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "创建失败", null);
            }

            // 处理 workflowData：创建任务和依赖，注入 jobId，内部会初始化状态
            // 内部已包含循环依赖验证（写数据库之前），验证失败会抛出RuntimeException
            processWorkflowData(workflow);

            // 直接返回 workflow 对象，无需再查库
            return ReturnT.ofSuccess(workflow);
        } catch (Exception e) {
            logger.error("创建工作流失败", e);
            // 检查是否是唯一约束冲突(作为兜底检查)
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("uk_project_folder_name")) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "该文件夹下已存在同名工作流", null);
            }
            return new ReturnT<>(ReturnT.FAIL_CODE, "创建失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ReturnT<DatapillarJobWorkflow> update(DatapillarJobWorkflow workflow) {
        if (workflow == null || workflow.getWorkflowId() == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "工作流ID不能为空");
        }

        // 检查是否存在
        DatapillarJobWorkflow existWorkflow = workflowMapper.loadById(workflow.getWorkflowId());
        if (existWorkflow == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "工作流不存在");
        }

        // 检查重复:排除自己
        DatapillarJobWorkflow duplicateWorkflow = workflowMapper.findByProjectIdAndFolderIdAndName(
            workflow.getProjectId(),
            workflow.getFolderId(),
            workflow.getName().trim()
        );
        if (duplicateWorkflow != null && !duplicateWorkflow.getWorkflowId().equals(workflow.getWorkflowId())) {
            if (workflow.getFolderId() == null) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "该项目下已存在同名工作流(未分类)");
            } else {
                return new ReturnT<>(ReturnT.FAIL_CODE, "该文件夹下已存在同名工作流");
            }
        }

        try {
            // 增量更新节点：新增INSERT，已存在UPDATE，删除DELETE
            // 内部会只对新增节点初始化状态
            updateWorkflowDataIncremental(workflow);

            // 更新工作流元数据
            int result = workflowMapper.update(workflow);
            if (result <= 0) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "更新失败");
            }

            // 直接返回 workflow 对象，无需再查库
            return ReturnT.ofSuccess(workflow);
        } catch (Exception e) {
            logger.error("更新工作流失败", e);
            // 检查是否是唯一约束冲突(作为兜底检查)
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("uk_project_folder_name")) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "该文件夹下已存在同名工作流");
            }
            return new ReturnT<>(ReturnT.FAIL_CODE, "更新失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<String> delete(Long workflowId) {
        if (workflowId == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "工作流ID不能为空");
        }

        try {
            int result = workflowMapper.delete(workflowId);
            return (result > 0) ? ReturnT.ofSuccess() : new ReturnT<>(ReturnT.FAIL_CODE, "删除失败");
        } catch (Exception e) {
            logger.error("删除工作流失败", e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "删除失败: " + e.getMessage());
        }
    }

    @Override
    public DatapillarJobWorkflow getById(Long workflowId) {
        return workflowMapper.loadById(workflowId);
    }

    @Override
    public List<DatapillarJobWorkflow> getByProjectId(Long projectId) {
        return workflowMapper.findByProjectId(projectId);
    }

    @Override
    public List<DatapillarJobWorkflow> getByFolderId(Long folderId) {
        return workflowMapper.findByFolderId(folderId);
    }

    @Override
    public List<DatapillarJobWorkflow> searchByName(Long projectId, String name) {
        return workflowMapper.searchByName(projectId, name);
    }

    @Override
    public List<DatapillarJobWorkflow> findAll() {
        return workflowMapper.findAll();
    }

    @Override
    public List<DatapillarJobWorkflow> findByStatus(String status) {
        return workflowMapper.findByStatus(status);
    }

    // ==================== 节点管理 ====================

    @Override
    public ReturnT<Map<String, Integer>> addNodesBatch(Long workflowId, List<WorkflowNodeDTO> nodeDTOs) {
        try {
            if (nodeDTOs == null || nodeDTOs.isEmpty()) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "节点列表不能为空");
            }

            Map<String, Integer> nodeToJobMap = new HashMap<>();

            for (WorkflowNodeDTO nodeDTO : nodeDTOs) {
                // 验证nodeType并映射到JobHandler
                String jobHandler = WorkflowNodeType.getJobHandlerByNodeType(nodeDTO.getNodeType());
                if (jobHandler == null) {
                    logger.warn("跳过不支持的节点类型: nodeId={}, nodeType={}", nodeDTO.getNodeId(), nodeDTO.getNodeType());
                    continue;
                }

                // 构建DatapillarJobInfo
                DatapillarJobInfo jobInfo = new DatapillarJobInfo();
                jobInfo.setJobGroup(1);
                jobInfo.setJobDesc(nodeDTO.getNodeName());
                jobInfo.setCreatedBy(nodeDTO.getCreatedBy());
                jobInfo.setExecutorHandler(jobHandler);
                jobInfo.setExecutorRouteStrategy("LOAD_BALANCED");
                jobInfo.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
                jobInfo.setExecutorParam(nodeDTO.getConfig());
                jobInfo.setGlueType(GlueTypeEnum.BEAN.name());
                jobInfo.setWorkflowId(workflowId);

                // 设置可选参数
                if (nodeDTO.getAlarmEmail() != null) {
                    jobInfo.setAlarmEmail(nodeDTO.getAlarmEmail());
                }
                if (nodeDTO.getExecutorTimeout() != null) {
                    jobInfo.setExecutorTimeout(nodeDTO.getExecutorTimeout());
                }
                if (nodeDTO.getExecutorFailRetryCount() != null) {
                    jobInfo.setExecutorFailRetryCount(nodeDTO.getExecutorFailRetryCount());
                }

                // 保存到数据库
                jobInfo.setGlueUpdatetime(new Date());
                datapillarJobInfoMapper.save(jobInfo);

                if (jobInfo.getId() > 0) {
                    nodeToJobMap.put(nodeDTO.getNodeId(), jobInfo.getId());
                    logger.info("批量添加节点成功: nodeId={}, jobId={}", nodeDTO.getNodeId(), jobInfo.getId());
                } else {
                    logger.error("批量添加节点失败: nodeId={}", nodeDTO.getNodeId());
                }
            }

            return ReturnT.ofSuccess(nodeToJobMap);
        } catch (Exception e) {
            logger.error("批量添加workflow节点失败: workflowId={}", workflowId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "批量添加节点失败: " + e.getMessage());
        }
    }


    @Override
    public ReturnT<String> deleteNode(Long workflowId, String nodeId) {
        try {
            // nodeId 实际上是 jobId（前端传递的是 jobId）
            Integer jobId = Integer.parseInt(nodeId);

            // 验证任务是否属于该工作流
            DatapillarJobInfo jobInfo = datapillarJobInfoMapper.loadById(jobId);
            if (jobInfo == null) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "任务不存在: jobId=" + jobId);
            }

            if (jobInfo.getWorkflowId() == null || !jobInfo.getWorkflowId().equals(workflowId)) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "任务不属于该工作流");
            }

            // 删除任务（外键约束会自动删除相关依赖）
            int result = datapillarJobInfoMapper.delete(jobId);

            if (result > 0) {
                logger.info("删除工作流节点成功: workflowId={}, jobId={}", workflowId, jobId);
                return ReturnT.ofSuccess("删除成功");
            } else {
                return new ReturnT<>(ReturnT.FAIL_CODE, "删除失败");
            }
        } catch (NumberFormatException e) {
            logger.error("节点ID格式错误: nodeId={}", nodeId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "节点ID格式错误");
        } catch (Exception e) {
            logger.error("删除工作流节点失败: workflowId={}, nodeId={}", workflowId, nodeId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "删除失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<List<DatapillarJobInfo>> getNodes(Long workflowId) {
        try {
            List<DatapillarJobInfo> nodes = datapillarJobInfoMapper.findByWorkflowId(workflowId);
            return ReturnT.ofSuccess(nodes);
        } catch (Exception e) {
            logger.error("查询workflow节点失败: workflowId={}", workflowId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询节点失败: " + e.getMessage());
        }
    }

    // ==================== 依赖管理 ====================

    @Override
    public ReturnT<String> batchAddDependencies(Long workflowId, List<String> dependencies, Map<String, Integer> nodeToJobMap) {
        try {
            // 验证workflow存在
            DatapillarJobWorkflow workflow = workflowMapper.loadById(workflowId);
            if (workflow == null) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "工作流不存在");
            }

            // 解析依赖关系并批量插入
            for (String dep : dependencies) {
                String[] parts = dep.split("->");
                if (parts.length != 2) {
                    logger.warn("无效的依赖关系格式: {}", dep);
                    continue;
                }

                String fromNodeId = parts[0].trim();
                String toNodeId = parts[1].trim();

                Integer fromJobId = nodeToJobMap.get(fromNodeId);
                Integer toJobId = nodeToJobMap.get(toNodeId);

                if (fromJobId == null || toJobId == null) {
                    logger.warn("节点映射不存在: fromNodeId={}, toJobId={}", fromNodeId, toNodeId);
                    continue;
                }

                // 检查依赖关系是否已存在
                DatapillarJobDependency existing = datapillarJobDependencyMapper.findByFromAndToJobId(fromJobId, toJobId);
                if (existing != null) {
                    logger.info("依赖关系已存在: fromJobId={}, toJobId={}", fromJobId, toJobId);
                    continue;
                }

                // 插入依赖关系
                DatapillarJobDependency dependency = new DatapillarJobDependency();
                dependency.setFromJobId(fromJobId);
                dependency.setToJobId(toJobId);
                dependency.setWorkflowId(workflowId);
                dependency.setDependencyType("NORMAL");

                datapillarJobDependencyMapper.insert(dependency);
            }

            return ReturnT.ofSuccess();
        } catch (Exception e) {
            logger.error("批量添加依赖失败: workflowId={}", workflowId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "批量添加依赖失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<String> deleteDependency(Long workflowId, Long dependencyId) {
        try {
            int result = datapillarJobDependencyMapper.deleteById(dependencyId.intValue());
            return (result > 0) ? ReturnT.ofSuccess() : new ReturnT<>(ReturnT.FAIL_CODE, "删除依赖失败");
        } catch (Exception e) {
            logger.error("删除依赖失败: dependencyId={}", dependencyId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "删除依赖失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<List<DatapillarJobDependency>> getDependencies(Long workflowId) {
        try {
            List<DatapillarJobDependency> dependencies = datapillarJobDependencyMapper.findByWorkflowId(workflowId);
            return ReturnT.ofSuccess(dependencies);
        } catch (Exception e) {
            logger.error("查询依赖失败: workflowId={}", workflowId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询依赖失败: " + e.getMessage());
        }
    }

    // ==================== 执行操作 ====================

    @Override
    public ReturnT<String> startWorkflow(long workflowId) {
        try {
            // 启动工作流(验证DAG并初始化任务状态)
            workflowExecutor.startWorkflow(workflowId);

            // 获取可以执行的任务(入度为0的任务)
            List<Integer> readyTasks = workflowExecutor.getReadyTasks(workflowId);

            // 触发初始任务(使用DAG类型,走正常流程)
            for (Integer jobId : readyTasks) {
                try {
                    JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.DAG, -1, null, null, null);
                    logger.info("提交工作流初始任务到触发池: workflowId={}, jobId={}", workflowId, jobId);
                } catch (Exception e) {
                    logger.error("触发初始任务失败: workflowId={}, jobId={}", workflowId, jobId, e);
                }
            }

            return ReturnT.ofSuccess();
        } catch (Exception e) {
            logger.error("启动工作流失败: workflowId={}", workflowId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "启动工作流失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<String> stopWorkflow(long workflowId) {
        try {
            workflowExecutor.stopWorkflow(workflowId);
            return ReturnT.ofSuccess();
        } catch (Exception e) {
            logger.error("停止工作流失败: workflowId={}", workflowId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "停止工作流失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<Map<String, Object>> getWorkflowDetail(long workflowId) {
        try {
            DatapillarJobWorkflow workflow = workflowExecutor.getWorkflow(workflowId);
            if (workflow == null) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "工作流不存在");
            }

            List<DatapillarJobInfo> tasks = workflowExecutor.getWorkflowTasks(workflowId);

            Map<String, Object> result = new HashMap<>();
            result.put("workflow", workflow);
            result.put("tasks", tasks);

            return ReturnT.ofSuccess(result);
        } catch (Exception e) {
            logger.error("查询工作流详情失败: workflowId={}", workflowId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<List<Integer>> getReadyTasks(long workflowId) {
        try {
            List<Integer> readyTasks = workflowExecutor.getReadyTasks(workflowId);
            return ReturnT.ofSuccess(readyTasks);
        } catch (Exception e) {
            logger.error("获取可执行任务失败: workflowId={}", workflowId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<String> rerunWorkflow(long workflowId) {
        try {
            workflowExecutor.rerunWorkflow(workflowId);

            List<Integer> readyTasks = workflowExecutor.getReadyTasks(workflowId);
            for (Integer jobId : readyTasks) {
                try {
                    JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.DAG, -1, null, null, null);
                    logger.info("重跑工作流-触发初始任务: workflowId={}, jobId={}", workflowId, jobId);
                } catch (Exception e) {
                    logger.error("触发初始任务失败: workflowId={}, jobId={}", workflowId, jobId, e);
                }
            }

            return ReturnT.ofSuccess();
        } catch (Exception e) {
            logger.error("重新运行工作流失败: workflowId={}", workflowId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "重新运行失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<String> retryTask(long workflowId, int jobId) {
        try {
            workflowExecutor.retryTask(workflowId, jobId);

            JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.MANUAL_SINGLE, -1, null, null, null);
            logger.info("重试任务: workflowId={}, jobId={}", workflowId, jobId);

            return ReturnT.ofSuccess();
        } catch (Exception e) {
            logger.error("重试任务失败: workflowId={}, jobId={}", workflowId, jobId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "重试任务失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<DatapillarJobInfo> runTask(long workflowId, int jobId) {
        try {
            // 1. 更新任务状态为 RUNNING 并设置开始时间
            workflowExecutor.updateTaskStatus(workflowId, jobId, "RUNNING");

            // 2. 注册 Future（先注册再 trigger，避免时序问题）
            java.util.concurrent.CompletableFuture<com.sunny.job.admin.model.DatapillarJobLog> future =
                com.sunny.job.admin.scheduler.thread.JobCallbackFutureHolder.registerFuture(jobId);

            // 3. 触发任务执行
            JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.MANUAL_SINGLE, -1, null, null, null);
            logger.info("已触发任务: workflowId={}, jobId={}", workflowId, jobId);

            // 4. 等待 executor 回调完成（不设超时，依赖 datapillar-job 的 monitor 兜底机制）
            com.sunny.job.admin.model.DatapillarJobLog log = future.get();
            logger.info("任务执行完成: workflowId={}, jobId={}, handleCode={}",
                workflowId, jobId, log.getHandleCode());

            // 5. 确定任务最终状态
            String finalStatus = log.getHandleCode() == com.sunny.job.core.biz.model.ReturnT.SUCCESS_CODE ? "COMPLETED" : "FAILED";

            // 6. 更新任务状态为 COMPLETED/FAILED 并设置结束时间
            workflowExecutor.updateTaskStatus(workflowId, jobId, finalStatus);

            // 7. 从 log 构造 DatapillarJobInfo（不查数据库）
            DatapillarJobInfo taskState = new DatapillarJobInfo();
            taskState.setWorkflowId(log.getWorkflowId());
            taskState.setId(log.getJobId());
            taskState.setStatus(finalStatus);
            taskState.setHandleMsg(log.getHandleMsg());
            taskState.setStartTime(log.getTriggerTime());  // 设置开始时间
            taskState.setEndTime(log.getHandleTime());

            // 8. 根据执行结果返回（成功和失败都返回 handleMsg）
            if ("FAILED".equals(taskState.getStatus())) {
                return new ReturnT<>(ReturnT.FAIL_CODE, log.getHandleMsg(), taskState);
            }

            return new ReturnT<>(ReturnT.SUCCESS_CODE, log.getHandleMsg(), taskState);
        } catch (Exception e) {
            logger.error("运行任务失败: workflowId={}, jobId={}", workflowId, jobId, e);
            // 清理 Future
            com.sunny.job.admin.scheduler.thread.JobCallbackFutureHolder.cancelFuture(jobId);
            return new ReturnT<>(ReturnT.FAIL_CODE, "运行任务失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<String> rerunFromTask(long workflowId, int startJobId) {
        try {
            workflowExecutor.rerunFromTask(workflowId, startJobId);

            JobTriggerPoolHelper.trigger(startJobId, TriggerTypeEnum.MANUAL_CASCADE, -1, null, null, null);
            logger.info("从任务重跑: workflowId={}, startJobId={}", workflowId, startJobId);

            return ReturnT.ofSuccess();
        } catch (Exception e) {
            logger.error("从任务重跑失败: workflowId={}, startJobId={}", workflowId, startJobId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "从任务重跑失败: " + e.getMessage());
        }
    }

    @Override
    public List<ProjectWorkflowSummaryDTO> getUserProjectsSummary(Long userId) {
        return workflowMapper.getUserProjectsSummary(userId);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 增量更新 workflowData：只处理变化的节点和依赖
     * 性能优化：一次查询获取所有旧节点，批量处理新增/更新/删除
     * @return nodeId -> jobId 映射
     */
    private Map<String, Integer> updateWorkflowDataIncremental(DatapillarJobWorkflow workflow) {
        String workflowDataStr = workflow.getWorkflowData();
        if (workflowDataStr == null || workflowDataStr.trim().isEmpty()) {
            logger.info("workflowData为空，跳过节点更新: workflowId={}", workflow.getWorkflowId());
            return new HashMap<>();
        }

        try {
            // 1. 一次查询获取所有旧节点（性能优化）
            List<DatapillarJobInfo> oldJobs = datapillarJobInfoMapper.findByWorkflowId(workflow.getWorkflowId());
            Map<Integer, DatapillarJobInfo> oldJobMap = new HashMap<>();
            for (DatapillarJobInfo job : oldJobs) {
                oldJobMap.put(job.getId(), job);
            }

            // 2. 解析前端 workflowData
            JSONObject workflowData = JSON.parseObject(workflowDataStr);
            JSONArray nodes = workflowData.getJSONArray("nodes");
            JSONArray edges = workflowData.getJSONArray("edges");

            if (nodes == null || nodes.isEmpty()) {
                logger.info("workflowData中没有节点，跳过: workflowId={}", workflow.getWorkflowId());
                return new HashMap<>();
            }

            // ✅ 在更新数据库之前，先验证循环依赖（不访问数据库）
            try {
                dagEngine.validateDAG(nodes, edges);
            } catch (com.sunny.job.admin.dag.CycleDetectedException e) {
                logger.error("工作流存在循环依赖: workflowId={}", workflow.getWorkflowId(), e);
                throw new RuntimeException("工作流存在循环依赖，无法保存: " + e.getMessage(), e);
            }

            Map<String, Integer> nodeToJobMap = new HashMap<>();
            Set<Integer> existingJobIds = new HashSet<>();
            List<Integer> newJobIds = new ArrayList<>();  // 收集新增节点的jobId

            // 3. 遍历前端节点：有jobId则UPDATE，无jobId则INSERT
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                String nodeId = node.getString("id");
                String nodeType = node.getString("type");
                JSONObject nodeData = node.getJSONObject("data");

                // 跳过start/end节点
                String jobHandler = WorkflowNodeType.getJobHandlerByNodeType(nodeType);
                if (jobHandler == null) {
                    logger.warn("跳过不支持的节点类型: nodeId={}, nodeType={}", nodeId, nodeType);
                    continue;
                }

                // 获取前端传来的jobId
                Integer jobId = nodeData != null ? nodeData.getInteger("jobId") : null;

                DatapillarJobInfo jobInfo = new DatapillarJobInfo();
                jobInfo.setJobGroup(1);
                jobInfo.setJobDesc(nodeData != null ? nodeData.getString("label") : nodeId);
                jobInfo.setCreatedBy(workflow.getCreatedBy());
                jobInfo.setExecutorHandler(jobHandler);
                jobInfo.setExecutorRouteStrategy("LOAD_BALANCED");
                jobInfo.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
                jobInfo.setGlueType(GlueTypeEnum.BEAN.name());
                jobInfo.setWorkflowId(workflow.getWorkflowId());

                if (nodeData != null) {
                    jobInfo.setExecutorParam(nodeData.toJSONString());

                    String alarmEmail = nodeData.getString("alarmEmail");
                    if (alarmEmail != null) {
                        jobInfo.setAlarmEmail(alarmEmail);
                    }
                    Integer executorTimeout = nodeData.getInteger("executorTimeout");
                    if (executorTimeout != null) {
                        jobInfo.setExecutorTimeout(executorTimeout);
                    }
                    Integer executorFailRetryCount = nodeData.getInteger("executorFailRetryCount");
                    if (executorFailRetryCount != null) {
                        jobInfo.setExecutorFailRetryCount(executorFailRetryCount);
                    }
                }

                if (jobId != null && jobId > 0) {
                    // 已存在的节点：UPDATE
                    jobInfo.setId(jobId);
                    jobInfo.setGlueUpdatetime(new Date());
                    datapillarJobInfoMapper.update(jobInfo);
                    nodeToJobMap.put(nodeId, jobId);
                    existingJobIds.add(jobId);
                    logger.info("更新节点: nodeId={}, jobId={}", nodeId, jobId);
                } else {
                    // 新增节点：INSERT
                    jobInfo.setGlueUpdatetime(new Date());
                    datapillarJobInfoMapper.save(jobInfo);
                    if (jobInfo.getId() > 0) {
                        nodeToJobMap.put(nodeId, jobInfo.getId());
                        existingJobIds.add(jobInfo.getId());
                        newJobIds.add(jobInfo.getId());  // 收集新增节点的jobId

                        // 注入 jobId 到 node.data
                        if (nodeData == null) {
                            nodeData = new JSONObject();
                            node.put("data", nodeData);
                        }
                        nodeData.put("jobId", jobInfo.getId());
                        logger.info("新增节点: nodeId={}, jobId={}", nodeId, jobInfo.getId());
                    }
                }
            }

            // 3.1. 批量初始化新增节点的状态（只对新增节点初始化）
            if (!newJobIds.isEmpty()) {
                datapillarJobInfoMapper.batchInitStatusByJobIds(workflow.getWorkflowId(), newJobIds);
                logger.info("批量初始化新增节点状态: workflowId={}, 新增节点数={}", workflow.getWorkflowId(), newJobIds.size());
            }

            // 4. 删除前端不存在的节点（级联删除state和dependency）
            for (Integer oldJobId : oldJobMap.keySet()) {
                if (!existingJobIds.contains(oldJobId)) {
                    datapillarJobInfoMapper.delete(oldJobId);
                    logger.info("删除节点: jobId={}", oldJobId);
                }
            }

            // 5. 更新依赖关系：只插入新的，删除节点时外键级联删除依赖
            if (edges != null && !edges.isEmpty()) {
                for (int i = 0; i < edges.size(); i++) {
                    JSONObject edge = edges.getJSONObject(i);
                    String sourceNodeId = edge.getString("source");
                    String targetNodeId = edge.getString("target");

                    Integer fromJobId = nodeToJobMap.get(sourceNodeId);
                    Integer toJobId = nodeToJobMap.get(targetNodeId);

                    if (fromJobId == null || toJobId == null) {
                        logger.warn("节点映射不存在，跳过依赖: source={}, target={}", sourceNodeId, targetNodeId);
                        continue;
                    }

                    // 检查依赖是否已存在
                    DatapillarJobDependency existing = datapillarJobDependencyMapper.findByFromAndToJobId(fromJobId, toJobId);
                    if (existing != null) {
                        continue; // 已存在，跳过
                    }

                    DatapillarJobDependency dependency = new DatapillarJobDependency();
                    dependency.setWorkflowId(workflow.getWorkflowId());
                    dependency.setFromJobId(fromJobId);
                    dependency.setToJobId(toJobId);
                    dependency.setDependencyType("SUCCESS");
                    datapillarJobDependencyMapper.insert(dependency);
                }
            }

            // 6. 更新 workflow 的 workflowData（包含 jobId）
            workflow.setWorkflowData(workflowData.toJSONString());
            // 注意：不在此处调用workflowMapper.update，由外层update方法统一更新，避免重复更新

            logger.info("增量更新完成: workflowId={}, 节点数={}, 依赖数={}",
                    workflow.getWorkflowId(), nodeToJobMap.size(), edges != null ? edges.size() : 0);

            return nodeToJobMap;
        } catch (Exception e) {
            logger.error("增量更新 workflowData 失败: workflowId={}", workflow.getWorkflowId(), e);
            throw new RuntimeException("增量更新工作流数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理 workflowData：解析JSON，创建任务和依赖，注入jobId
     * @return nodeId -> jobId 映射
     */
    private Map<String, Integer> processWorkflowData(DatapillarJobWorkflow workflow) {
        String workflowDataStr = workflow.getWorkflowData();
        if (workflowDataStr == null || workflowDataStr.trim().isEmpty()) {
            logger.info("workflowData为空，跳过节点和依赖创建: workflowId={}", workflow.getWorkflowId());
            return new HashMap<>();
        }

        try {
            // 1. 解析 workflowData
            JSONObject workflowData = JSON.parseObject(workflowDataStr);
            JSONArray nodes = workflowData.getJSONArray("nodes");
            JSONArray edges = workflowData.getJSONArray("edges");

            if (nodes == null || nodes.isEmpty()) {
                logger.info("workflowData中没有节点，跳过: workflowId={}", workflow.getWorkflowId());
                return new HashMap<>();
            }

            // ✅ 在写入数据库之前，先验证循环依赖（不访问数据库）
            try {
                dagEngine.validateDAG(nodes, edges);
            } catch (com.sunny.job.admin.dag.CycleDetectedException e) {
                logger.error("工作流存在循环依赖: workflowId={}", workflow.getWorkflowId(), e);
                throw new RuntimeException("工作流存在循环依赖，无法保存: " + e.getMessage(), e);
            }

            // 1. 第一步：遍历nodes准备所有job对象（不插入数据库）
            List<DatapillarJobInfo> jobInfoList = new ArrayList<>();
            Map<String, DatapillarJobInfo> nodeIdToJobInfoMap = new HashMap<>();

            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                String nodeId = node.getString("id");
                String nodeType = node.getString("type");
                JSONObject nodeData = node.getJSONObject("data");

                // 跳过start/end节点
                String jobHandler = WorkflowNodeType.getJobHandlerByNodeType(nodeType);
                if (jobHandler == null) {
                    logger.warn("跳过不支持的节点类型: nodeId={}, nodeType={}", nodeId, nodeType);
                    continue;
                }

                // 构建 DatapillarJobInfo对象（不插入数据库）
                DatapillarJobInfo jobInfo = new DatapillarJobInfo();
                jobInfo.setJobGroup(1);
                jobInfo.setJobDesc(nodeData != null ? nodeData.getString("label") : nodeId);
                jobInfo.setCreatedBy(workflow.getCreatedBy());
                jobInfo.setExecutorHandler(jobHandler);
                jobInfo.setExecutorRouteStrategy("LOAD_BALANCED");
                jobInfo.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
                jobInfo.setGlueType(GlueTypeEnum.BEAN.name());
                jobInfo.setWorkflowId(workflow.getWorkflowId());
                jobInfo.setGlueUpdatetime(new Date());

                if (nodeData != null) {
                    jobInfo.setExecutorParam(nodeData.toJSONString());

                    String alarmEmail = nodeData.getString("alarmEmail");
                    if (alarmEmail != null) {
                        jobInfo.setAlarmEmail(alarmEmail);
                    }
                    Integer executorTimeout = nodeData.getInteger("executorTimeout");
                    if (executorTimeout != null) {
                        jobInfo.setExecutorTimeout(executorTimeout);
                    }
                    Integer executorFailRetryCount = nodeData.getInteger("executorFailRetryCount");
                    if (executorFailRetryCount != null) {
                        jobInfo.setExecutorFailRetryCount(executorFailRetryCount);
                    }
                }

                jobInfoList.add(jobInfo);
                nodeIdToJobInfoMap.put(nodeId, jobInfo);
            }

            // 2. 第二步：批量INSERT所有jobs（一次SQL，MyBatis自动填充ID）
            if (!jobInfoList.isEmpty()) {
                datapillarJobInfoMapper.batchInsert(jobInfoList);
                logger.info("批量创建节点: workflowId={}, 节点数={}", workflow.getWorkflowId(), jobInfoList.size());
            }

            // 3. 第三步：注入jobId到workflowData JSON
            Map<String, Integer> nodeToJobMap = new HashMap<>();
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                String nodeId = node.getString("id");
                DatapillarJobInfo jobInfo = nodeIdToJobInfoMap.get(nodeId);

                if (jobInfo != null && jobInfo.getId() > 0) {
                    nodeToJobMap.put(nodeId, jobInfo.getId());

                    // 注入 jobId 到 node.data
                    JSONObject nodeData = node.getJSONObject("data");
                    if (nodeData == null) {
                        nodeData = new JSONObject();
                        node.put("data", nodeData);
                    }
                    nodeData.put("jobId", jobInfo.getId());
                    logger.info("节点jobId注入成功: nodeId={}, jobId={}", nodeId, jobInfo.getId());
                }
            }

            // 4. 第四步：批量创建依赖关系
            if (edges != null && !edges.isEmpty()) {
                List<DatapillarJobDependency> dependencies = new ArrayList<>();

                for (int i = 0; i < edges.size(); i++) {
                    JSONObject edge = edges.getJSONObject(i);
                    String sourceNodeId = edge.getString("source");
                    String targetNodeId = edge.getString("target");

                    Integer fromJobId = nodeToJobMap.get(sourceNodeId);
                    Integer toJobId = nodeToJobMap.get(targetNodeId);

                    if (fromJobId == null || toJobId == null) {
                        logger.warn("节点映射不存在，跳过依赖: source={}, target={}", sourceNodeId, targetNodeId);
                        continue;
                    }

                    // 准备依赖关系对象
                    DatapillarJobDependency dependency = new DatapillarJobDependency();
                    dependency.setWorkflowId(workflow.getWorkflowId());
                    dependency.setFromJobId(fromJobId);
                    dependency.setToJobId(toJobId);
                    dependency.setDependencyType("SUCCESS");

                    dependencies.add(dependency);
                }

                // 批量插入所有依赖关系
                if (!dependencies.isEmpty()) {
                    datapillarJobDependencyMapper.batchInsert(dependencies);
                    logger.info("批量创建依赖: workflowId={}, 依赖数={}", workflow.getWorkflowId(), dependencies.size());
                }
            }

            // 5. 第五步：批量初始化所有节点状态为PENDING（利用数据库默认值）
            if (!nodeToJobMap.isEmpty()) {
                List<Integer> jobIds = new ArrayList<>(nodeToJobMap.values());
                datapillarJobInfoMapper.batchInitStatusByJobIds(workflow.getWorkflowId(), jobIds);
                logger.info("初始化工作流任务状态: workflowId={}, 任务数={}", workflow.getWorkflowId(), jobIds.size());
            }

            // 6. 第六步：更新workflow的workflowData（包含jobId）
            workflow.setWorkflowData(workflowData.toJSONString());
            workflowMapper.update(workflow);

            logger.info("处理 workflowData 完成: workflowId={}, 节点数={}, 依赖数={}",
                    workflow.getWorkflowId(), nodeToJobMap.size(), edges != null ? edges.size() : 0);

            return nodeToJobMap;
        } catch (Exception e) {
            logger.error("处理 workflowData 失败: workflowId={}", workflow.getWorkflowId(), e);
            throw new RuntimeException("处理工作流数据失败: " + e.getMessage(), e);
        }
    }
}
