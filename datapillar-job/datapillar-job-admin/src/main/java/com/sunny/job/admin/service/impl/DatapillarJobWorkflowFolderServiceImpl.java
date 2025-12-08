package com.sunny.job.admin.service.impl;

import com.sunny.job.admin.mapper.DatapillarJobWorkflowFolderMapper;
import com.sunny.job.admin.mapper.DatapillarJobWorkflowMapper;
import com.sunny.job.admin.model.DatapillarJobWorkflow;
import com.sunny.job.admin.model.DatapillarJobWorkflowFolder;
import com.sunny.job.admin.service.DatapillarJobWorkflowFolderService;
import com.sunny.job.admin.util.I18nUtil;
import com.sunny.job.core.biz.model.ReturnT;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * datapillar-job workflow folder service impl
 *
 * @author datapillar-job-admin
 * @date 2025-11-09
 */
@Service
public class DatapillarJobWorkflowFolderServiceImpl implements DatapillarJobWorkflowFolderService {

    private static final Logger logger = LoggerFactory.getLogger(DatapillarJobWorkflowFolderServiceImpl.class);

    @Resource
    private DatapillarJobWorkflowFolderMapper folderMapper;

    @Resource
    private DatapillarJobWorkflowMapper workflowMapper;

    @Override
    public ReturnT<Long> create(DatapillarJobWorkflowFolder folder) {
        // 校验参数
        if (folder == null || folder.getName() == null || folder.getName().trim().isEmpty()) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "文件夹名称不能为空");
        }

        if (folder.getProjectId() == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "项目ID不能为空");
        }

        // 检查同名文件夹
        int count = folderMapper.countByParentIdAndName(folder.getParentId(), folder.getName());
        if (count > 0) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "同一层级下已存在同名文件夹");
        }

        // 插入
        try {
            int result = folderMapper.insert(folder);
            return (result > 0) ? ReturnT.ofSuccess(folder.getId())
                                : new ReturnT<Long>(ReturnT.FAIL_CODE, "创建失败", null);
        } catch (Exception e) {
            logger.error("创建文件夹失败", e);
            return new ReturnT<Long>(ReturnT.FAIL_CODE, "创建失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ReturnT<String> update(DatapillarJobWorkflowFolder folder) {
        if (folder == null || folder.getId() == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "文件夹ID不能为空");
        }

        // 检查是否存在
        DatapillarJobWorkflowFolder existFolder = folderMapper.selectById(folder.getId());
        if (existFolder == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "文件夹不存在");
        }

        try {
            int result = folderMapper.update(folder);
            return (result > 0) ? ReturnT.ofSuccess() : new ReturnT<>(ReturnT.FAIL_CODE, "更新失败");
        } catch (Exception e) {
            logger.error("更新文件夹失败", e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "更新失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<String> delete(Long id) {
        if (id == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "文件夹ID不能为空");
        }

        try {
            int result = folderMapper.deleteById(id);
            return (result > 0) ? ReturnT.ofSuccess() : new ReturnT<>(ReturnT.FAIL_CODE, "删除失败");
        } catch (Exception e) {
            logger.error("删除文件夹失败", e);
            return new ReturnT<>(ReturnT.FAIL_CODE, "删除失败: " + e.getMessage());
        }
    }

    @Override
    public DatapillarJobWorkflowFolder getById(Long id) {
        return folderMapper.selectById(id);
    }

    @Override
    public List<DatapillarJobWorkflowFolder> getByProjectId(Long projectId) {
        return folderMapper.selectByProjectId(projectId);
    }

    @Override
    public List<DatapillarJobWorkflowFolder> getRootFolders(Long projectId) {
        return folderMapper.selectRootFolders(projectId);
    }

    @Override
    public ReturnT<String> moveWorkflow(Long workflowId, Long targetFolderId) {
        if (workflowId == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "工作流ID不能为空");
        }

        try {
            // 检查工作流是否存在
            DatapillarJobWorkflow workflow = workflowMapper.loadById(workflowId);
            if (workflow == null) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "工作流不存在");
            }

            // 如果指定了目标文件夹,检查文件夹是否存在
            if (targetFolderId != null) {
                DatapillarJobWorkflowFolder targetFolder = folderMapper.selectById(targetFolderId);
                if (targetFolder == null) {
                    return new ReturnT<>(ReturnT.FAIL_CODE, "目标文件夹不存在");
                }
            }

            // 更新工作流的folderId
            workflow.setFolderId(targetFolderId);
            int result = workflowMapper.update(workflow);

            return (result > 0) ? ReturnT.ofSuccess() : new ReturnT<>(ReturnT.FAIL_CODE, "移动失败");
        } catch (Exception e) {
            logger.error("移动工作流失败: workflowId={}, targetFolderId={}", workflowId, targetFolderId, e);
            // 检查是否是唯一约束冲突
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("uk_project_folder_name")) {
                return new ReturnT<>(ReturnT.FAIL_CODE, "目标文件夹下已存在同名工作流");
            }
            return new ReturnT<>(ReturnT.FAIL_CODE, "移动失败: " + e.getMessage());
        }
    }
}
