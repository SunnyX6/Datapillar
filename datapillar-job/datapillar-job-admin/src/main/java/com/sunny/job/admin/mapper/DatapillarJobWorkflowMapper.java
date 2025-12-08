package com.sunny.job.admin.mapper;

import com.sunny.job.admin.dto.ProjectWorkflowSummaryDTO;
import com.sunny.job.admin.model.DatapillarJobWorkflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * datapillar-job workflow mapper
 *
 * @author datapillar-job-admin
 * @date 2025-11-06
 */
@Mapper
public interface DatapillarJobWorkflowMapper {

    /**
     * 插入工作流实例
     */
    public int insert(DatapillarJobWorkflow workflow);

    /**
     * 根据工作流ID加载工作流
     */
    public DatapillarJobWorkflow loadById(@Param("workflowId") long workflowId);

    /**
     * 更新工作流状态
     */
    public int updateStatus(DatapillarJobWorkflow workflow);

    /**
     * 更新工作流结束时间
     */
    public int updateEndTime(@Param("workflowId") long workflowId,
                            @Param("endTime") java.util.Date endTime);

    /**
     * 查找所有工作流实例
     */
    public List<DatapillarJobWorkflow> findAll();

    /**
     * 根据状态查找工作流实例
     */
    public List<DatapillarJobWorkflow> findByStatus(@Param("status") String status);

    /**
     * 分页查询工作流实例
     */
    public List<DatapillarJobWorkflow> pageList(@Param("offset") int offset,
                                        @Param("pagesize") int pagesize,
                                        @Param("status") String status,
                                        @Param("startTimeStart") String startTimeStart,
                                        @Param("startTimeEnd") String startTimeEnd);

    /**
     * 分页查询总数
     */
    public int pageListCount(@Param("offset") int offset,
                            @Param("pagesize") int pagesize,
                            @Param("status") String status,
                            @Param("startTimeStart") String startTimeStart,
                            @Param("startTimeEnd") String startTimeEnd);

    /**
     * 删除工作流实例
     */
    public int delete(@Param("workflowId") long workflowId);

    /**
     * 更新工作流信息
     */
    public int update(DatapillarJobWorkflow workflow);

    /**
     * 根据项目ID查询工作流
     */
    public List<DatapillarJobWorkflow> findByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据文件夹ID查询工作流
     */
    public List<DatapillarJobWorkflow> findByFolderId(@Param("folderId") Long folderId);

    /**
     * 根据名称搜索工作流
     */
    public List<DatapillarJobWorkflow> searchByName(@Param("projectId") Long projectId, @Param("name") String name);

    /**
     * 根据项目ID、文件夹ID和名称查找工作流（精确匹配，用于唯一性校验）
     * 支持folderId为null的情况
     */
    public DatapillarJobWorkflow findByProjectIdAndFolderIdAndName(
            @Param("projectId") Long projectId,
            @Param("folderId") Long folderId,
            @Param("name") String name);

    /**
     * 获取用户所有项目的工作流统计信息
     * @param userId 用户ID
     * @return 项目工作流统计列表
     */
    public List<ProjectWorkflowSummaryDTO> getUserProjectsSummary(@Param("userId") Long userId);
}