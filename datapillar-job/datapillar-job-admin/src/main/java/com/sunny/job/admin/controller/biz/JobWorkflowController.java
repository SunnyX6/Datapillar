package com.sunny.job.admin.controller.biz;

import com.sunny.job.admin.dto.ProjectWorkflowSummaryDTO;
import com.sunny.job.admin.dto.WorkflowNodeDTO;
import com.sunny.job.admin.model.DatapillarJobDependency;
import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobWorkflow;
import com.sunny.job.admin.service.DatapillarJobWorkflowService;
import com.sunny.job.core.biz.model.ReturnT;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作流管理控制器
 * 负责workflow的完整生命周期管理：CRUD、节点管理、依赖管理、执行控制
 * 所有端点遵循RESTful规范，以workflow为中心组织
 *
 * @author sunny
 * @date 2025-11-10
 */
@Controller
@RequestMapping("/workflow")
public class JobWorkflowController {
    private static final Logger logger = LoggerFactory.getLogger(JobWorkflowController.class);

    @Resource
    private DatapillarJobWorkflowService workflowService;

    // ==================== Workflow CRUD ====================

    /**
     * 保存或更新工作流(统一端点)
     * 如果workflow中有workflowId则更新，否则创建
     */
    @RequestMapping(value = "/save/workflow", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<DatapillarJobWorkflow> saveOrUpdate(@RequestBody DatapillarJobWorkflow workflow) {
        try {
            if (workflow.getWorkflowId() != null && workflow.getWorkflowId() > 0) {
                // 更新现有工作流，直接返回 Service 层的结果（无需再查库）
                return workflowService.update(workflow);
            } else {
                // 创建新工作流，直接返回 Service 层的结果（无需再查库）
                return workflowService.create(workflow);
            }
        } catch (Exception e) {
            logger.error("保存工作流失败", e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "保存失败: " + e.getMessage());
        }
    }

    /**
     * 更新工作流状态
     */
    @RequestMapping(value = "/update/status", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> updateWorkflowStatus(
            @RequestParam("workflowId") Long workflowId,
            @RequestParam("status") String status) {
        try {
            DatapillarJobWorkflow workflow = workflowService.getById(workflowId);
            if (workflow == null) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "工作流不存在");
            }
            workflow.setStatus(status);
            ReturnT<DatapillarJobWorkflow> result = workflowService.update(workflow);
            if (result.getCode() == ReturnT.SUCCESS_CODE) {
                return ReturnT.ofSuccess("状态更新成功");
            } else {
                return new ReturnT<>(ReturnT.FAIL_CODE, result.getMsg());
            }
        } catch (Exception e) {
            logger.error("更新工作流状态失败", e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "更新状态失败: " + e.getMessage());
        }
    }

    /**
     * 删除workflow
     */
    @RequestMapping(value = "/delete/workflow", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> deleteWorkflow(@RequestParam("workflowId") Long workflowId) {
        return workflowService.delete(workflowId);
    }

    /**
     * 查询workflow详情（包含任务状态）
     */
    @RequestMapping(value = "/get/detail", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<Map<String, Object>> getWorkflowDetail(@RequestParam("workflowId") Long workflowId) {
        return workflowService.getWorkflowDetail(workflowId);
    }

    /**
     * 查询所有workflow列表
     */
    @RequestMapping(value = "/list/all", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<DatapillarJobWorkflow>> listWorkflows() {
        try {
            List<DatapillarJobWorkflow> workflows = workflowService.findAll();
            return ReturnT.ofSuccess(workflows);
        } catch (Exception e) {
            logger.error("查询工作流列表失败", e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 按状态查询workflow
     */
    @RequestMapping(value = "/list/byStatus", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<DatapillarJobWorkflow>> listByStatus(@RequestParam("status") String status) {
        try {
            List<DatapillarJobWorkflow> workflows = workflowService.findByStatus(status);
            return ReturnT.ofSuccess(workflows);
        } catch (Exception e) {
            logger.error("按状态查询工作流失败: status={}", status, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据项目ID查询workflow列表
     */
    @RequestMapping(value = "/list/byProject", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<DatapillarJobWorkflow>> listByProject(@RequestParam("projectId") Long projectId) {
        try {
            List<DatapillarJobWorkflow> workflows = workflowService.getByProjectId(projectId);
            return ReturnT.ofSuccess(workflows);
        } catch (Exception e) {
            logger.error("查询项目工作流失败: projectId={}", projectId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据文件夹ID查询workflow列表
     */
    @RequestMapping(value = "/list/byFolder", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<DatapillarJobWorkflow>> listByFolder(@RequestParam("folderId") Long folderId) {
        try {
            List<DatapillarJobWorkflow> workflows = workflowService.getByFolderId(folderId);
            return ReturnT.ofSuccess(workflows);
        } catch (Exception e) {
            logger.error("查询文件夹工作流失败: folderId={}", folderId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据名称搜索workflow
     */
    @RequestMapping(value = "/search/byName", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<DatapillarJobWorkflow>> searchWorkflows(@RequestParam("projectId") Long projectId,
                                                          @RequestParam("name") String name) {
        try {
            List<DatapillarJobWorkflow> workflows = workflowService.searchByName(projectId, name);
            return ReturnT.ofSuccess(workflows);
        } catch (Exception e) {
            logger.error("搜索工作流失败", e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "搜索失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户所有项目的工作流统计信息
     * 返回每个项目的工作流总数、今日新增、今日成功、今日失败、运行中任务数、成功率等统计数据
     */
    @RequestMapping(value = "/get/userProjectsSummary", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<ProjectWorkflowSummaryDTO>> getUserProjectsSummary(@RequestParam("userId") Long userId) {
        try {
            List<ProjectWorkflowSummaryDTO> summary = workflowService.getUserProjectsSummary(userId);
            return ReturnT.ofSuccess(summary);
        } catch (Exception e) {
            logger.error("获取用户项目统计失败: userId={}", userId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "获取统计失败: " + e.getMessage());
        }
    }

    // ==================== Workflow节点管理 ====================

    /**
     * 批量添加workflow节点
     */
    @RequestMapping(value = "/add/nodesBatch", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<Map<String, Integer>> addNodesBatch(@RequestParam("workflowId") Long workflowId,
                                                        @RequestBody List<WorkflowNodeDTO> nodeDTOs) {
        return workflowService.addNodesBatch(workflowId, nodeDTOs);
    }
    /**
     * 删除workflow节点
     */
    @RequestMapping(value = "/delete/node", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> deleteNode(@RequestParam("workflowId") Long workflowId,
                                      @RequestParam("nodeId") String nodeId) {
        return workflowService.deleteNode(workflowId, nodeId);
    }

    /**
     * 查询workflow的所有节点
     */
    @RequestMapping(value = "/get/nodes", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<DatapillarJobInfo>> getNodes(@RequestParam("workflowId") Long workflowId) {
        return workflowService.getNodes(workflowId);
    }

    // ==================== Workflow依赖管理 ====================

    /**
     * 批量添加依赖（基于前端连线）
     */
    @RequestMapping(value = "/add/dependenciesBatch", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> batchAddDependencies(@RequestParam("workflowId") Long workflowId,
                                                 @RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<String> dependencies = (List<String>) params.get("dependencies");
        @SuppressWarnings("unchecked")
        Map<String, Integer> nodeToJobMap = (Map<String, Integer>) params.get("nodeToJobMap");
        return workflowService.batchAddDependencies(workflowId, dependencies, nodeToJobMap);
    }

    /**
     * 删除依赖
     */
    @RequestMapping(value = "/delete/dependency", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> deleteDependency(@RequestParam("workflowId") Long workflowId,
                                            @RequestParam("dependencyId") Long dependencyId) {
        return workflowService.deleteDependency(workflowId, dependencyId);
    }

    /**
     * 查询workflow的所有依赖
     */
    @RequestMapping(value = "/get/dependencies", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<DatapillarJobDependency>> getDependencies(@RequestParam("workflowId") Long workflowId) {
        return workflowService.getDependencies(workflowId);
    }

    // ==================== Workflow执行控制 ====================

    /**
     * 启动workflow执行
     */
    @RequestMapping(value = "/start/workflow", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> startWorkflow(@RequestParam("workflowId") Long workflowId) {
        return workflowService.startWorkflow(workflowId);
    }

    /**
     * 停止workflow
     */
    @RequestMapping(value = "/stop/workflow", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> stopWorkflow(@RequestParam("workflowId") Long workflowId) {
        return workflowService.stopWorkflow(workflowId);
    }

    /**
     * 重跑整个workflow
     */
    @RequestMapping(value = "/rerun/workflow", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> rerunWorkflow(@RequestParam("workflowId") Long workflowId) {
        return workflowService.rerunWorkflow(workflowId);
    }

    /**
     * 运行单个任务（返回任务状态）
     */
    @RequestMapping(value = "/run/task", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<DatapillarJobInfo> runTask(@RequestParam("workflowId") Long workflowId,
                                        @RequestParam("jobId") Integer jobId) {
        return workflowService.runTask(workflowId, jobId);
    }

    /**
     * 从某个节点开始重跑
     */
    @RequestMapping(value = "/rerun/fromTask", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> rerunFromTask(@RequestParam("workflowId") Long workflowId,
                                         @RequestParam("jobId") Integer jobId) {
        return workflowService.rerunFromTask(workflowId, jobId);
    }

    /**
     * 获取可执行任务
     */
    @RequestMapping(value = "/get/readyTasks", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<Integer>> getReadyTasks(@RequestParam("workflowId") Long workflowId) {
        return workflowService.getReadyTasks(workflowId);
    }
}
