package com.sunny.job.admin.service.impl;

import com.sunny.job.admin.mapper.DatapillarJobGroupMapper;
import com.sunny.job.admin.mapper.DatapillarJobInfoMapper;
import com.sunny.job.admin.mapper.DatapillarJobLogMapper;
import com.sunny.job.admin.model.DatapillarJobGroup;
import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobLog;
import com.sunny.job.admin.scheduler.complete.DatapillarJobCompleter;
import com.sunny.job.admin.scheduler.exception.DatapillarJobException;
import com.sunny.job.admin.scheduler.scheduler.DatapillarJobScheduler;
import com.sunny.job.admin.service.DatapillarJobLogService;
import com.sunny.job.admin.util.CollectionTool;
import com.sunny.job.admin.util.I18nUtil;
import com.sunny.job.admin.util.StringTool;
import com.sunny.job.core.biz.ExecutorBiz;
import com.sunny.job.core.biz.model.KillParam;
import com.sunny.job.core.biz.model.LogParam;
import com.sunny.job.core.biz.model.LogResult;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.util.DateUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务日志服务实现
 *
 * @author sunny
 * @date 2025-11-10
 */
@Service
public class DatapillarJobLogServiceImpl implements DatapillarJobLogService {
    private static final Logger logger = LoggerFactory.getLogger(DatapillarJobLogServiceImpl.class);

    @Resource
    private DatapillarJobGroupMapper datapillarJobGroupMapper;

    @Resource
    private DatapillarJobInfoMapper datapillarJobInfoMapper;

    @Resource
    private DatapillarJobLogMapper datapillarJobLogMapper;

    @Override
    public ReturnT<Object[]> loadLogPageData(Integer jobGroup, Integer jobId) {
        // 查询所有执行器组
        List<DatapillarJobGroup> jobGroupList = datapillarJobGroupMapper.findAll();
        if (CollectionTool.isEmpty(jobGroupList)) {
            return new ReturnT<>(ReturnT.FAIL_CODE, I18nUtil.getString("jobgroup_empty"));
        }

        // 解析jobId、jobGroup
        if (jobId != null && jobId > 0) {
            DatapillarJobInfo jobInfo = datapillarJobInfoMapper.loadById(jobId);
            if (jobInfo == null) {
                return new ReturnT<>(ReturnT.FAIL_CODE,
                        I18nUtil.getString("jobinfo_field_id") + I18nUtil.getString("system_unvalid"));
            }
            jobGroup = jobInfo.getJobGroup();
        } else if (jobGroup != null && jobGroup > 0) {
            jobId = 0;
        } else {
            jobGroup = jobGroupList.get(0).getId();
            jobId = 0;
        }

        // 查询执行器组下的任务列表
        List<DatapillarJobInfo> jobInfoList = datapillarJobInfoMapper.getJobsByGroup(jobGroup);

        return ReturnT.ofSuccess(new Object[]{jobGroupList, jobGroup, jobId, jobInfoList});
    }

    @Override
    public Map<String, Object> pageList(int start, int length, int jobGroup, int jobId,
                                        Date triggerTimeStart, Date triggerTimeEnd, int logStatus) {
        // 分页查询
        List<DatapillarJobLog> list = datapillarJobLogMapper.pageList(start, length, jobGroup, jobId,
                triggerTimeStart, triggerTimeEnd, logStatus);
        int listCount = datapillarJobLogMapper.pageListCount(start, length, jobGroup, jobId,
                triggerTimeStart, triggerTimeEnd, logStatus);

        // 封装结果
        Map<String, Object> maps = new HashMap<>();
        maps.put("recordsTotal", listCount);
        maps.put("recordsFiltered", listCount);
        maps.put("data", list);
        return maps;
    }

    @Override
    public ReturnT<DatapillarJobLog> loadLogDetail(int id) {
        DatapillarJobLog jobLog = datapillarJobLogMapper.load(id);
        if (jobLog == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, I18nUtil.getString("joblog_logid_unvalid"));
        }
        return ReturnT.ofSuccess(jobLog);
    }

    @Override
    public ReturnT<LogResult> logDetailCat(long logId, int fromLineNum) {
        try {
            // 验证日志
            DatapillarJobLog jobLog = datapillarJobLogMapper.load(logId);
            if (jobLog == null) {
                return ReturnT.ofFail(I18nUtil.getString("joblog_logid_unvalid"));
            }

            // 查询日志内容
            ExecutorBiz executorBiz = DatapillarJobScheduler.getExecutorBiz(jobLog.getExecutorAddress());
            ReturnT<LogResult> logResult = executorBiz.log(new LogParam(jobLog.getTriggerTime().getTime(), logId, fromLineNum));

            // 判断是否结束
            if (logResult.getContent() != null && logResult.getContent().getFromLineNum() > logResult.getContent().getToLineNum()) {
                if (jobLog.getHandleCode() > 0) {
                    logResult.getContent().setEnd(true);
                }
            }

            // 修复XSS
            if (logResult.getContent() != null && StringTool.isNotBlank(logResult.getContent().getLogContent())) {
                String newLogContent = filterXss(logResult.getContent().getLogContent());
                logResult.getContent().setLogContent(newLogContent);
            }

            return logResult;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ReturnT.ofFail(e.getMessage());
        }
    }

    @Override
    public ReturnT<String> logKill(int id) {
        // 基础检查
        DatapillarJobLog log = datapillarJobLogMapper.load(id);
        if (log == null) {
            return ReturnT.ofFail(I18nUtil.getString("joblog_logid_unvalid"));
        }

        DatapillarJobInfo jobInfo = datapillarJobInfoMapper.loadById(log.getJobId());
        if (jobInfo == null) {
            return ReturnT.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        if (ReturnT.SUCCESS_CODE != log.getTriggerCode()) {
            return ReturnT.ofFail(I18nUtil.getString("joblog_kill_log_limit"));
        }

        // 请求终止
        ReturnT<String> runResult;
        try {
            ExecutorBiz executorBiz = DatapillarJobScheduler.getExecutorBiz(log.getExecutorAddress());
            runResult = executorBiz.kill(new KillParam(jobInfo.getId()));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            runResult = ReturnT.ofFail(e.getMessage());
        }

        if (ReturnT.SUCCESS_CODE == runResult.getCode()) {
            log.setHandleCode(ReturnT.FAIL_CODE);
            log.setHandleMsg(I18nUtil.getString("joblog_kill_log_byman") + ":" + (runResult.getMsg() != null ? runResult.getMsg() : ""));
            log.setHandleTime(new Date());
            DatapillarJobCompleter.updateHandleInfoAndFinish(log);
            return ReturnT.ofSuccess(runResult.getMsg());
        } else {
            return ReturnT.ofFail(runResult.getMsg());
        }
    }

    @Override
    public ReturnT<String> clearLog(int jobGroup, int jobId, int type) {
        // 确定清理条件
        Date clearBeforeTime = null;
        int clearBeforeNum = 0;

        switch (type) {
            case 1:
                clearBeforeTime = DateUtil.addMonths(new Date(), -1);
                break;
            case 2:
                clearBeforeTime = DateUtil.addMonths(new Date(), -3);
                break;
            case 3:
                clearBeforeTime = DateUtil.addMonths(new Date(), -6);
                break;
            case 4:
                clearBeforeTime = DateUtil.addYears(new Date(), -1);
                break;
            case 5:
                clearBeforeNum = 1000;
                break;
            case 6:
                clearBeforeNum = 10000;
                break;
            case 7:
                clearBeforeNum = 30000;
                break;
            case 8:
                clearBeforeNum = 100000;
                break;
            case 9:
                clearBeforeNum = 0;
                break;
            default:
                return ReturnT.ofFail(I18nUtil.getString("joblog_clean_type_unvalid"));
        }

        // 批量清理
        List<Long> logIds;
        do {
            logIds = datapillarJobLogMapper.findClearLogIds(jobGroup, jobId, clearBeforeTime, clearBeforeNum, 1000);
            if (logIds != null && !logIds.isEmpty()) {
                datapillarJobLogMapper.clearLog(logIds);
            }
        } while (logIds != null && !logIds.isEmpty());

        return ReturnT.ofSuccess();
    }

    /**
     * 过滤XSS标签
     */
    private String filterXss(String originData) {
        // 排除标签
        Map<String, String> excludeTagMap = new HashMap<>();
        excludeTagMap.put("<br>", "###TAG_BR###");
        excludeTagMap.put("<b>", "###TAG_BOLD###");
        excludeTagMap.put("</b>", "###TAG_BOLD_END###");

        // 替换
        for (Map.Entry<String, String> entry : excludeTagMap.entrySet()) {
            originData = originData.replaceAll(entry.getKey(), entry.getValue());
        }

        // HTML转义
        originData = HtmlUtils.htmlEscape(originData, "UTF-8");

        // 替换回来
        for (Map.Entry<String, String> entry : excludeTagMap.entrySet()) {
            originData = originData.replaceAll(entry.getValue(), entry.getKey());
        }

        return originData;
    }
}
