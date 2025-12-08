package com.sunny.job.core.handler.impl;

import com.sunny.job.core.context.DatapillarJobContext;
import com.sunny.job.core.context.DatapillarJobHelper;
import com.sunny.job.core.glue.GlueTypeEnum;
import com.sunny.job.core.handler.IJobHandler;
import com.sunny.job.core.log.DatapillarJobFileAppender;
import com.sunny.job.core.util.ScriptUtil;

import java.io.File;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class ScriptJobHandler extends IJobHandler {

    private int jobId;
    private long glueUpdatetime;
    private String gluesource;
    private GlueTypeEnum glueType;

    public ScriptJobHandler(int jobId, long glueUpdatetime, String gluesource, GlueTypeEnum glueType){
        this.jobId = jobId;
        this.glueUpdatetime = glueUpdatetime;
        this.gluesource = gluesource;
        this.glueType = glueType;

        // clean old script file
        File glueSrcPath = new File(DatapillarJobFileAppender.getGlueSrcPath());
        if (glueSrcPath.exists()) {
            File[] glueSrcFileList = glueSrcPath.listFiles();
            if (glueSrcFileList!=null && glueSrcFileList.length>0) {
                for (File glueSrcFileItem : glueSrcFileList) {
                    if (glueSrcFileItem.getName().startsWith(String.valueOf(jobId)+"_")) {
                        glueSrcFileItem.delete();
                    }
                }
            }
        }

    }

    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    @Override
    public void execute() throws Exception {

        if (!glueType.isScript()) {
            DatapillarJobHelper.handleFail("glueType["+ glueType +"] invalid.");
            return;
        }

        // cmd
        String cmd = glueType.getCmd();

        // make script file
        String scriptFileName = DatapillarJobFileAppender.getGlueSrcPath()
                .concat(File.separator)
                .concat(String.valueOf(jobId))
                .concat("_")
                .concat(String.valueOf(glueUpdatetime))
                .concat(glueType.getSuffix());
        File scriptFile = new File(scriptFileName);
        if (!scriptFile.exists()) {
            ScriptUtil.markScriptFile(scriptFileName, gluesource);
        }

        // log file
        String logFileName = DatapillarJobContext.getDatapillarJobContext().getJobLogFileName();

        // script params：0=param、1=分片序号、2=分片总数
        String[] scriptParams = new String[3];
        scriptParams[0] = DatapillarJobHelper.getJobParam();
        scriptParams[1] = String.valueOf(DatapillarJobContext.getDatapillarJobContext().getShardIndex());
        scriptParams[2] = String.valueOf(DatapillarJobContext.getDatapillarJobContext().getShardTotal());

        // invoke
        DatapillarJobHelper.log("----------- script file:"+ scriptFileName +" -----------");
        int exitValue = ScriptUtil.execToFile(cmd, scriptFileName, logFileName, scriptParams);

        if (exitValue == 0) {
            DatapillarJobHelper.handleSuccess();
            return;
        } else {
            DatapillarJobHelper.handleFail("script exit value("+exitValue+") is failed");
            return ;
        }

    }

}
