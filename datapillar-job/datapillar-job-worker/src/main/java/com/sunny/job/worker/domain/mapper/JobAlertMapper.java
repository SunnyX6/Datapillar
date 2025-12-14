package com.sunny.job.worker.domain.mapper;

import com.sunny.job.worker.domain.entity.JobAlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 告警 Mapper
 * <p>
 * 处理 job_alarm_rule 和 job_alarm_log
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobAlertMapper {

    /**
     * 查询任务的告警规则
     *
     * @param jobId 任务ID
     * @return 告警规则列表
     */
    List<JobAlertRule> selectRulesByJobId(@Param("jobId") Long jobId);

    /**
     * 插入告警记录
     *
     * @param namespaceId   命名空间ID
     * @param workflowRunId 工作流执行实例ID
     * @param jobRunId      任务执行实例ID
     * @param ruleId        告警规则ID
     * @param channelId     告警渠道ID
     * @param alarmType     告警类型: 1-告警 2-恢复
     * @param title         告警标题
     * @param content       告警内容
     * @param status        发送状态: 0-待发送 1-成功 2-失败
     * @param sendResult    发送结果
     * @return 影响行数
     */
    int insertAlertLog(@Param("namespaceId") Long namespaceId,
                       @Param("workflowRunId") Long workflowRunId,
                       @Param("jobRunId") Long jobRunId,
                       @Param("ruleId") Long ruleId,
                       @Param("channelId") Long channelId,
                       @Param("alarmType") Integer alarmType,
                       @Param("title") String title,
                       @Param("content") String content,
                       @Param("status") Integer status,
                       @Param("sendResult") String sendResult);
}
