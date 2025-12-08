package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * datapillar-job dependency mapper
 *
 * @author datapillar-job-admin
 * @date 2025-11-06
 */
@Mapper
public interface DatapillarJobDependencyMapper {

    /**
     * 插入依赖关系
     */
    public int insert(DatapillarJobDependency dependency);

    /**
     * 根据ID删除依赖关系
     */
    public int deleteById(@Param("id") int id);

    /**
     * 根据被依赖任务ID查找依赖关系
     */
    public List<DatapillarJobDependency> findByFromJobId(@Param("fromJobId") int fromJobId);

    /**
     * 根据依赖任务ID查找依赖关系
     */
    public List<DatapillarJobDependency> findByToJobId(@Param("toJobId") int toJobId);

    /**
     * 查找所有依赖关系
     */
    public List<DatapillarJobDependency> findAll();

    /**
     * 根据workflow ID查找依赖关系
     */
    public List<DatapillarJobDependency> findByWorkflowId(@Param("workflowId") long workflowId);

    /**
     * 根据任务ID删除相关依赖关系（包括被依赖和依赖）
     */
    public int deleteByJobId(@Param("jobId") int jobId);

    /**
     * 检查依赖关系是否存在
     */
    public DatapillarJobDependency findByFromAndToJobId(@Param("fromJobId") int fromJobId,
                                                 @Param("toJobId") int toJobId);

    /**
     * 更新依赖类型
     */
    public int updateDependencyType(@Param("id") int id,
                                   @Param("dependencyType") String dependencyType);

    /**
     * 批量插入依赖关系
     */
    public int batchInsert(@Param("dependencies") List<DatapillarJobDependency> dependencies);
}