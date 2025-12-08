package com.sunny.job.core.biz.client;

import com.sunny.job.core.biz.ExecutorBiz;
import com.sunny.job.core.biz.model.*;
import com.sunny.job.core.util.DatapillarJobRemotingUtil;

/**
 * admin api test
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class ExecutorBizClient implements ExecutorBiz {

    public ExecutorBizClient() {
    }
    public ExecutorBizClient(String addressUrl, String accessToken, int timeout) {
        this.addressUrl = addressUrl;
        this.accessToken = accessToken;
        this.timeout = timeout;

        // valid
        if (!this.addressUrl.endsWith("/")) {
            this.addressUrl = this.addressUrl + "/";
        }
        if (!(this.timeout >=1 && this.timeout <= 10)) {
            this.timeout = 3;
        }
    }

    private String addressUrl ;
    private String accessToken;
    private int timeout;


    @Override
    public ReturnT<String> beat() {
        return DatapillarJobRemotingUtil.postBody(addressUrl+"beat", accessToken, timeout, "", String.class);
    }

    @Override
    public ReturnT<String> idleBeat(IdleBeatParam idleBeatParam){
        return DatapillarJobRemotingUtil.postBody(addressUrl+"idleBeat", accessToken, timeout, idleBeatParam, String.class);
    }

    @Override
    public ReturnT<String> run(TriggerParam triggerParam) {
        return DatapillarJobRemotingUtil.postBody(addressUrl + "run", accessToken, timeout, triggerParam, String.class);
    }

    @Override
    public ReturnT<String> kill(KillParam killParam) {
        return DatapillarJobRemotingUtil.postBody(addressUrl + "kill", accessToken, timeout, killParam, String.class);
    }

    @Override
    public ReturnT<LogResult> log(LogParam logParam) {
        return DatapillarJobRemotingUtil.postBody(addressUrl + "log", accessToken, timeout, logParam, LogResult.class);
    }

    @Override
    public ReturnT<LogResult> debugRun(TriggerParam triggerParam) {
        return DatapillarJobRemotingUtil.postBody(addressUrl + "debugRun", accessToken, 300, triggerParam, LogResult.class);
    }

}
