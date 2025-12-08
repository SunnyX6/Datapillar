package com.sunny.job.admin.service;

import com.sunny.job.admin.model.DatapillarJobGroup;
import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobLog;
import com.sunny.job.core.biz.model.LogResult;
import com.sunny.job.core.biz.model.ReturnT;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 任务日志服务
 *
 * @author sunny
 * @date 2025-11-10
 */
public interface DatapillarJobLogService {

    /**
     * 加载日志页面初始化数据
     *
     * @param jobGroup 执行器组ID
     * @param jobId    任务ID
     * @return [jobGroupList, jobGroup, jobId, jobInfoList]
     */
    ReturnT<Object[]> loadLogPageData(Integer jobGroup, Integer jobId);

    /**
     * 分页查询日志列表
     *
     * @param start            起始位置
     * @param length           每页条数
     * @param jobGroup         执行器组ID
     * @param jobId            任务ID
     * @param triggerTimeStart 触发时间起始
     * @param triggerTimeEnd   触发时间结束
     * @param logStatus        日志状态
     * @return 分页数据
     */
    Map<String, Object> pageList(int start, int length, int jobGroup, int jobId,
                                  Date triggerTimeStart, Date triggerTimeEnd, int logStatus);

    /**
     * 加载日志详情
     *
     * @param id 日志ID
     * @return 日志对象
     */
    ReturnT<DatapillarJobLog> loadLogDetail(int id);

    /**
     * 查询日志详细内容
     *
     * @param logId       日志ID
     * @param fromLineNum 起始行号
     * @return 日志内容
     */
    ReturnT<LogResult> logDetailCat(long logId, int fromLineNum);

    /**
     * 终止任务
     *
     * @param id 日志ID
     * @return 终止结果
     */
    ReturnT<String> logKill(int id);

    /**
     * 清理日志
     *
     * @param jobGroup 执行器组ID
     * @param jobId    任务ID
     * @param type     清理类型
     * @return 清理结果
     */
    ReturnT<String> clearLog(int jobGroup, int jobId, int type);
}
