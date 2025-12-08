package com.sunny.job.admin.controller.biz;

import com.sunny.job.admin.model.DatapillarJobWorkflowFolder;
import com.sunny.job.admin.service.DatapillarJobWorkflowFolderService;
import com.sunny.job.core.biz.model.ReturnT;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流文件夹管理控制器
 *
 * @author datapillar-job-admin
 * @date 2025-11-09
 */
@Controller
@RequestMapping("/workflow/folder")
public class JobWorkflowFolderController {
    private static final Logger logger = LoggerFactory.getLogger(JobWorkflowFolderController.class);

    @Resource
    private DatapillarJobWorkflowFolderService folderService;

    /**
     * 创建文件夹
     */
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<Long> createFolder(@RequestBody DatapillarJobWorkflowFolder folder) {
        return folderService.create(folder);
    }

    /**
     * 更新文件夹
     */
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> updateFolder(@RequestBody DatapillarJobWorkflowFolder folder) {
        return folderService.update(folder);
    }

    /**
     * 删除文件夹
     */
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> deleteFolder(@RequestParam("folderId") Long folderId) {
        return folderService.delete(folderId);
    }

    /**
     * 根据ID查询文件夹
     */
    @RequestMapping(value = "/get", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<DatapillarJobWorkflowFolder> getFolder(@RequestParam("folderId") Long folderId) {
        try {
            DatapillarJobWorkflowFolder folder = folderService.getById(folderId);
            if (folder == null) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "文件夹不存在", null);
            }
            return ReturnT.ofSuccess(folder);
        } catch (Exception e) {
            logger.error("查询文件夹失败", e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询失败: " + e.getMessage(), null);
        }
    }

    /**
     * 根据项目ID查询文件夹列表
     */
    @RequestMapping(value = "/list/byProject", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<DatapillarJobWorkflowFolder>> listByProject(@RequestParam("projectId") Long projectId) {
        try {
            List<DatapillarJobWorkflowFolder> folders = folderService.getByProjectId(projectId);
            return ReturnT.ofSuccess(folders);
        } catch (Exception e) {
            logger.error("查询项目文件夹失败: projectId={}", projectId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询失败: " + e.getMessage(), null);
        }
    }

    /**
     * 获取根文件夹列表
     */
    @RequestMapping(value = "/list/root", method = RequestMethod.GET)
    @ResponseBody
    public ReturnT<List<DatapillarJobWorkflowFolder>> listRootFolders(@RequestParam("projectId") Long projectId) {
        try {
            List<DatapillarJobWorkflowFolder> folders = folderService.getRootFolders(projectId);
            return ReturnT.ofSuccess(folders);
        } catch (Exception e) {
            logger.error("查询根文件夹失败: projectId={}", projectId, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "查询失败: " + e.getMessage(), null);
        }
    }

    /**
     * 移动工作流到指定文件夹
     */
    @RequestMapping(value = "/move/workflow", method = RequestMethod.POST)
    @ResponseBody
    public ReturnT<String> moveWorkflow(
            @RequestParam("workflowId") Long workflowId,
            @RequestParam(value = "folderId", required = false) Long targetFolderId) {
        return folderService.moveWorkflow(workflowId, targetFolderId);
    }
}
