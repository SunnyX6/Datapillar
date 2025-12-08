package com.sunny.job.core.biz.client;

import com.sunny.job.core.biz.AdminBiz;
import com.sunny.job.core.biz.model.HandleCallbackParam;
import com.sunny.job.core.biz.model.RegistryParam;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.util.DatapillarJobRemotingUtil;

import java.util.List;

/**
 * admin api test
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class AdminBizClient implements AdminBiz {

    public AdminBizClient() {
    }
    public AdminBizClient(String addressUrl, String accessToken, int timeout) {
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
    public ReturnT<String> callback(List<HandleCallbackParam> callbackParamList) {
        return DatapillarJobRemotingUtil.postBody(addressUrl+"datapillar-job/callback", accessToken, timeout, callbackParamList, String.class);
    }

    @Override
    public ReturnT<String> registry(RegistryParam registryParam) {
        return DatapillarJobRemotingUtil.postBody(addressUrl + "datapillar-job/registry", accessToken, timeout, registryParam, String.class);
    }

    @Override
    public ReturnT<String> registryRemove(RegistryParam registryParam) {
        return DatapillarJobRemotingUtil.postBody(addressUrl + "datapillar-job/registryRemove", accessToken, timeout, registryParam, String.class);
    }

}
