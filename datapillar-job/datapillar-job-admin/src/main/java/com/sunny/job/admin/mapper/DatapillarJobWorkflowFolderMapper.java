package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobWorkflowFolder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * datapillar-job workflow folder mapper
 *
 * @author datapillar-job-admin
 * @date 2025-11-09
 */
@Mapper
public interface DatapillarJobWorkflowFolderMapper {

    /**
     * 新增文件夹
     */
    int insert(DatapillarJobWorkflowFolder folder);

    /**
     * 更新文件夹
     */
    int update(DatapillarJobWorkflowFolder folder);

    /**
     * 根据ID删除文件夹
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据ID查询文件夹
     */
    DatapillarJobWorkflowFolder selectById(@Param("id") Long id);

    /**
     * 根据项目ID查询所有文件夹
     */
    List<DatapillarJobWorkflowFolder> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据父文件夹ID查询子文件夹
     */
    List<DatapillarJobWorkflowFolder> selectByParentId(@Param("parentId") Long parentId);

    /**
     * 查询所有根文件夹（没有父文件夹的）
     */
    List<DatapillarJobWorkflowFolder> selectRootFolders(@Param("projectId") Long projectId);

    /**
     * 检查同一父文件夹下是否存在同名文件夹
     */
    int countByParentIdAndName(@Param("parentId") Long parentId, @Param("name") String name);
}
