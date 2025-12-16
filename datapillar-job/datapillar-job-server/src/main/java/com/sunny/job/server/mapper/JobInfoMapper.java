package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务定义 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {

    /**
     * 查询工作流下所有任务
     */
    List<JobInfo> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 查询工作流下所有任务ID
     */
    List<Long> selectJobIdsByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 根据ID和工作流ID查询任务
     */
    JobInfo selectByIdAndWorkflowId(@Param("id") Long id, @Param("workflowId") Long workflowId);

    /**
     * 逻辑删除工作流下所有任务
     */
    int softDeleteByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 删除任务
     */
    int deleteByIdAndWorkflowId(@Param("id") Long id, @Param("workflowId") Long workflowId);

    /**
     * 更新任务位置
     */
    int updatePosition(@Param("id") Long id, @Param("workflowId") Long workflowId,
                       @Param("positionX") Double positionX, @Param("positionY") Double positionY);

    /**
     * 更新任务信息
     */
    int updateJob(@Param("job") JobInfo job);
}
