package com.sunny.job.core.context;

import com.sunny.job.core.log.DatapillarJobFileAppender;
import com.sunny.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

/**
 * helper for datapillar-job
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class DatapillarJobHelper {

    // ---------------------- base info ----------------------

    /**
     * current JobId
     *
     * @return
     */
    public static long getJobId() {
        DatapillarJobContext datapillarJobContext = DatapillarJobContext.getDatapillarJobContext();
        if (datapillarJobContext == null) {
            return -1;
        }

        return datapillarJobContext.getJobId();
    }

    /**
     * current JobParam
     *
     * @return
     */
    public static String getJobParam() {
        DatapillarJobContext datapillarJobContext = DatapillarJobContext.getDatapillarJobContext();
        if (datapillarJobContext == null) {
            return null;
        }

        return datapillarJobContext.getJobParam();
    }

    // ---------------------- for log ----------------------

    /**
     * current JobLogFileName
     *
     * @return
     */
    public static String getJobLogFileName() {
        DatapillarJobContext datapillarJobContext = DatapillarJobContext.getDatapillarJobContext();
        if (datapillarJobContext == null) {
            return null;
        }

        return datapillarJobContext.getJobLogFileName();
    }

    // ---------------------- for shard ----------------------

    /**
     * current ShardIndex
     *
     * @return
     */
    public static int getShardIndex() {
        DatapillarJobContext datapillarJobContext = DatapillarJobContext.getDatapillarJobContext();
        if (datapillarJobContext == null) {
            return -1;
        }

        return datapillarJobContext.getShardIndex();
    }

    /**
     * current ShardTotal
     *
     * @return
     */
    public static int getShardTotal() {
        DatapillarJobContext datapillarJobContext = DatapillarJobContext.getDatapillarJobContext();
        if (datapillarJobContext == null) {
            return -1;
        }

        return datapillarJobContext.getShardTotal();
    }

    // ---------------------- tool for log ----------------------

    private static Logger logger = LoggerFactory.getLogger("datapillar-job logger");

    /**
     * append log with pattern
     *
     * @param appendLogPattern  like "aaa {} bbb {} ccc"
     * @param appendLogArguments    like "111, true"
     */
    public static boolean log(String appendLogPattern, Object ... appendLogArguments) {

        FormattingTuple ft = MessageFormatter.arrayFormat(appendLogPattern, appendLogArguments);
        String appendLog = ft.getMessage();

        /*appendLog = appendLogPattern;
        if (appendLogArguments!=null && appendLogArguments.length>0) {
            appendLog = MessageFormat.format(appendLogPattern, appendLogArguments);
        }*/

        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
        return logDetail(callInfo, appendLog);
    }

    /**
     * append exception stack
     *
     * @param e
     */
    public static boolean log(Throwable e) {

        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        String appendLog = stringWriter.toString();

        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
        return logDetail(callInfo, appendLog);
    }

    /**
     * append log
     *
     * @param callInfo
     * @param appendLog
     */
    private static boolean logDetail(StackTraceElement callInfo, String appendLog) {
        DatapillarJobContext datapillarJobContext = DatapillarJobContext.getDatapillarJobContext();
        if (datapillarJobContext == null) {
            return false;
        }

        /*// "yyyy-MM-dd HH:mm:ss [ClassName]-[MethodName]-[LineNumber]-[ThreadName] log";
        StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();
        StackTraceElement callInfo = stackTraceElements[1];*/

        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(DateUtil.formatDateTime(new Date())).append(" ")
                .append("["+ callInfo.getClassName() + "#" + callInfo.getMethodName() +"]").append("-")
                .append("["+ callInfo.getLineNumber() +"]").append("-")
                .append("["+ Thread.currentThread().getName() +"]").append(" ")
                .append(appendLog!=null?appendLog:"");
        String formatAppendLog = stringBuffer.toString();

        // appendlog
        String logFileName = datapillarJobContext.getJobLogFileName();

        if (logFileName!=null && logFileName.trim().length()>0) {
            DatapillarJobFileAppender.appendLog(logFileName, formatAppendLog);
            return true;
        } else {
            logger.info(">>>>>>>>>>> {}", formatAppendLog);
            return false;
        }
    }

    // ---------------------- tool for handleResult ----------------------

    /**
     * handle success
     *
     * @return
     */
    public static boolean handleSuccess(){
        return handleResult(DatapillarJobContext.HANDLE_CODE_SUCCESS, null);
    }

    /**
     * handle success with log msg
     *
     * @param handleMsg
     * @return
     */
    public static boolean handleSuccess(String handleMsg) {
        return handleResult(DatapillarJobContext.HANDLE_CODE_SUCCESS, handleMsg);
    }

    /**
     * handle fail
     *
     * @return
     */
    public static boolean handleFail(){
        return handleResult(DatapillarJobContext.HANDLE_CODE_FAIL, null);
    }

    /**
     * handle fail with log msg
     *
     * @param handleMsg
     * @return
     */
    public static boolean handleFail(String handleMsg) {
        return handleResult(DatapillarJobContext.HANDLE_CODE_FAIL, handleMsg);
    }

    /**
     * handle timeout
     *
     * @return
     */
    public static boolean handleTimeout(){
        return handleResult(DatapillarJobContext.HANDLE_CODE_TIMEOUT, null);
    }

    /**
     * handle timeout with log msg
     *
     * @param handleMsg
     * @return
     */
    public static boolean handleTimeout(String handleMsg){
        return handleResult(DatapillarJobContext.HANDLE_CODE_TIMEOUT, handleMsg);
    }

    /**
     * @param handleCode
     *
     *      200 : success
     *      500 : fail
     *      502 : timeout
     *
     * @param handleMsg
     * @return
     */
    public static boolean handleResult(int handleCode, String handleMsg) {
        DatapillarJobContext datapillarJobContext = DatapillarJobContext.getDatapillarJobContext();
        if (datapillarJobContext == null) {
            return false;
        }

        datapillarJobContext.setHandleCode(handleCode);
        if (handleMsg != null) {
            datapillarJobContext.setHandleMsg(handleMsg);
        }
        return true;
    }


}
