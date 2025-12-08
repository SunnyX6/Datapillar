package com.sunny.job.core.biz.impl;

import com.sunny.job.core.biz.ExecutorBiz;
import com.sunny.job.core.biz.model.*;
import com.sunny.job.core.enums.ExecutorBlockStrategyEnum;
import com.sunny.job.core.executor.DatapillarJobExecutor;
import com.sunny.job.core.glue.GlueFactory;
import com.sunny.job.core.glue.GlueTypeEnum;
import com.sunny.job.core.handler.IJobHandler;
import com.sunny.job.core.handler.impl.GlueJobHandler;
import com.sunny.job.core.handler.impl.ScriptJobHandler;
import com.sunny.job.core.log.DatapillarJobFileAppender;
import com.sunny.job.core.thread.JobThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class ExecutorBizImpl implements ExecutorBiz {
    private static Logger logger = LoggerFactory.getLogger(ExecutorBizImpl.class);

    @Override
    public ReturnT<String> beat() {
        return ReturnT.ofSuccess();
    }

    @Override
    public ReturnT<String> idleBeat(IdleBeatParam idleBeatParam) {

        // isRunningOrHasQueue
        boolean isRunningOrHasQueue = false;
        JobThread jobThread = DatapillarJobExecutor.loadJobThread(idleBeatParam.getJobId());
        if (jobThread != null && jobThread.isRunningOrHasQueue()) {
            isRunningOrHasQueue = true;
        }

        if (isRunningOrHasQueue) {
            return ReturnT.ofFail("job thread is running or has trigger queue.");
        }
        return ReturnT.ofSuccess();
    }

    @Override
    public ReturnT<String> run(TriggerParam triggerParam) {
        // load old：jobHandler + jobThread
        JobThread jobThread = DatapillarJobExecutor.loadJobThread(triggerParam.getJobId());
        IJobHandler jobHandler = jobThread!=null?jobThread.getHandler():null;
        String removeOldReason = null;

        // valid：jobHandler + jobThread
        GlueTypeEnum glueTypeEnum = GlueTypeEnum.match(triggerParam.getGlueType());
        if (GlueTypeEnum.BEAN == glueTypeEnum) {

            // new jobhandler
            IJobHandler newJobHandler = DatapillarJobExecutor.loadJobHandler(triggerParam.getExecutorHandler());

            // valid old jobThread
            if (jobThread!=null && jobHandler != newJobHandler) {
                // change handler, need kill old thread
                removeOldReason = "change jobhandler or glue type, and terminate the old job thread.";

                jobThread = null;
                jobHandler = null;
            }

            // valid handler
            if (jobHandler == null) {
                jobHandler = newJobHandler;
                if (jobHandler == null) {
                    return ReturnT.ofFail( "job handler [" + triggerParam.getExecutorHandler() + "] not found.");
                }
            }

        } else if (GlueTypeEnum.GLUE_GROOVY == glueTypeEnum) {

            // valid old jobThread
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof GlueJobHandler
                        && ((GlueJobHandler) jobThread.getHandler()).getGlueUpdatetime()==triggerParam.getGlueUpdatetime() )) {
                // change handler or gluesource updated, need kill old thread
                removeOldReason = "change job source or glue type, and terminate the old job thread.";

                jobThread = null;
                jobHandler = null;
            }

            // valid handler
            if (jobHandler == null) {
                try {
                    IJobHandler originJobHandler = GlueFactory.getInstance().loadNewInstance(triggerParam.getGlueSource());
                    jobHandler = new GlueJobHandler(originJobHandler, triggerParam.getGlueUpdatetime());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    return ReturnT.ofFail( e.getMessage());
                }
            }
        } else if (glueTypeEnum!=null && glueTypeEnum.isScript()) {

            // valid old jobThread
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof ScriptJobHandler
                            && ((ScriptJobHandler) jobThread.getHandler()).getGlueUpdatetime()==triggerParam.getGlueUpdatetime() )) {
                // change script or gluesource updated, need kill old thread
                removeOldReason = "change job source or glue type, and terminate the old job thread.";

                jobThread = null;
                jobHandler = null;
            }

            // valid handler
            if (jobHandler == null) {
                jobHandler = new ScriptJobHandler(triggerParam.getJobId(), triggerParam.getGlueUpdatetime(), triggerParam.getGlueSource(), GlueTypeEnum.match(triggerParam.getGlueType()));
            }
        } else {
            return ReturnT.ofFail("glueType[" + triggerParam.getGlueType() + "] is not valid.");
        }

        // executor block strategy
        if (jobThread != null) {
            ExecutorBlockStrategyEnum blockStrategy = ExecutorBlockStrategyEnum.match(triggerParam.getExecutorBlockStrategy(), null);
            if (ExecutorBlockStrategyEnum.DISCARD_LATER == blockStrategy) {
                // discard when running
                if (jobThread.isRunningOrHasQueue()) {
                    return ReturnT.ofFail("block strategy effect："+ExecutorBlockStrategyEnum.DISCARD_LATER.getTitle());
                }
            } else if (ExecutorBlockStrategyEnum.COVER_EARLY == blockStrategy) {
                // kill running jobThread
                if (jobThread.isRunningOrHasQueue()) {
                    removeOldReason = "block strategy effect：" + ExecutorBlockStrategyEnum.COVER_EARLY.getTitle();

                    jobThread = null;
                }
            } else {
                // just queue trigger
            }
        }

        // replace thread (new or exists invalid)
        if (jobThread == null) {
            jobThread = DatapillarJobExecutor.registJobThread(triggerParam.getJobId(), jobHandler, removeOldReason);
        }

        // push data to queue
        ReturnT<String> pushResult = jobThread.pushTriggerQueue(triggerParam);
        return pushResult;
    }

    @Override
    public ReturnT<String> kill(KillParam killParam) {
        // kill handlerThread, and create new one
        JobThread jobThread = DatapillarJobExecutor.loadJobThread(killParam.getJobId());
        if (jobThread != null) {
            DatapillarJobExecutor.removeJobThread(killParam.getJobId(), "scheduling center kill job.");
            return ReturnT.ofSuccess();
        }

        return ReturnT.ofSuccess( "job thread already killed.");
    }

    @Override
    public ReturnT<LogResult> log(LogParam logParam) {
        // log filename: logPath/yyyy-MM-dd/9999.log
        String logFileName = DatapillarJobFileAppender.makeLogFileName(new Date(logParam.getLogDateTim()), logParam.getLogId());

        LogResult logResult = DatapillarJobFileAppender.readLog(logFileName, logParam.getFromLineNum());
        return ReturnT.ofSuccess(logResult);
    }

    @Override
    public ReturnT<LogResult> debugRun(TriggerParam triggerParam) {
        logger.info(">>>>>>>>>>> datapillar-job debugRun start, jobId:{}, handler:{}",
                triggerParam.getJobId(), triggerParam.getExecutorHandler());

        // 1. 加载handler
        IJobHandler jobHandler = DatapillarJobExecutor.loadJobHandler(triggerParam.getExecutorHandler());
        if (jobHandler == null) {
            String errorMsg = "job handler [" + triggerParam.getExecutorHandler() + "] not found.";
            logger.error(errorMsg);
            return ReturnT.ofFail(errorMsg);
        }

        // 2. 创建临时日志文件
        String logFileName = null;
        java.io.File tempLogFile = null;
        int handleCode = 500;
        String handleMsg = null;

        try {
            // 创建临时日志文件用于收集输出
            tempLogFile = java.io.File.createTempFile("datapillar_job_debug_", ".log");
            logFileName = tempLogFile.getAbsolutePath();

            // 创建DatapillarJobContext（使用临时日志文件）
            com.sunny.job.core.context.DatapillarJobContext datapillarJobContext =
                    new com.sunny.job.core.context.DatapillarJobContext(
                            triggerParam.getJobId(),
                            triggerParam.getExecutorParams(),
                            logFileName,
                            triggerParam.getBroadcastIndex(),
                            triggerParam.getBroadcastTotal());

            com.sunny.job.core.context.DatapillarJobContext.setDatapillarJobContext(datapillarJobContext);

            // 执行handler
            jobHandler.execute();

            // 获取执行结果
            handleCode = datapillarJobContext.getHandleCode();
            handleMsg = datapillarJobContext.getHandleMsg();

            logger.info(">>>>>>>>>>> datapillar-job debugRun success, jobId:{}, handleCode:{}",
                    triggerParam.getJobId(), handleCode);

        } catch (Exception e) {
            logger.error(">>>>>>>>>>> datapillar-job debugRun error, jobId:{}", triggerParam.getJobId(), e);
            handleCode = 500;
            handleMsg = e.getMessage();

            // 记录异常到日志文件
            if (logFileName != null) {
                DatapillarJobFileAppender.appendLog(logFileName, "\n!!! 执行异常 !!!\n" + e.toString());
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                DatapillarJobFileAppender.appendLog(logFileName, sw.toString());
            }
        } finally {
            com.sunny.job.core.context.DatapillarJobContext.setDatapillarJobContext(null);
        }

        // 3. 读取日志文件内容
        String logContent = "";
        if (tempLogFile != null && tempLogFile.exists()) {
            try {
                logContent = new String(java.nio.file.Files.readAllBytes(tempLogFile.toPath()),
                                       java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.error("读取临时日志文件失败", e);
                logContent = "读取日志失败: " + e.getMessage();
            } finally {
                // 删除临时日志文件
                tempLogFile.delete();
            }
        }

        // 4. 返回完整日志
        LogResult logResult = new LogResult(1, 1, logContent, true);
        return ReturnT.ofSuccess(logResult);
    }

}
