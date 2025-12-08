package com.sunny.job.admin.service;

import com.sunny.job.admin.model.DatapillarJobWorkflowFolder;
import com.sunny.job.core.biz.model.ReturnT;

import java.util.List;

/**
 * datapillar-job workflow folder service
 *
 * @author datapillar-job-admin
 * @date 2025-11-09
 */
public interface DatapillarJobWorkflowFolderService {

    /**
     * 创建文件夹
     */
    ReturnT<Long> create(DatapillarJobWorkflowFolder folder);

    /**
     * 更新文件夹
     */
    ReturnT<String> update(DatapillarJobWorkflowFolder folder);

    /**
     * 删除文件夹
     */
    ReturnT<String> delete(Long id);

    /**
     * 根据ID查询文件夹
     */
    DatapillarJobWorkflowFolder getById(Long id);

    /**
     * 根据项目ID查询文件夹树
     */
    List<DatapillarJobWorkflowFolder> getByProjectId(Long projectId);

    /**
     * 获取根文件夹列表
     */
    List<DatapillarJobWorkflowFolder> getRootFolders(Long projectId);

    /**
     * 移动工作流到指定文件夹
     * @param workflowId 工作流ID
     * @param targetFolderId 目标文件夹ID,为null表示移动到根目录(未分类)
     */
    ReturnT<String> moveWorkflow(Long workflowId, Long targetFolderId);
}
