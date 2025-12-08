package com.sunny.job.admin.service.impl;

import com.sunny.job.admin.scheduler.thread.JobCompleteHelper;
import com.sunny.job.admin.scheduler.thread.JobRegistryHelper;
import com.sunny.job.core.biz.AdminBiz;
import com.sunny.job.core.biz.model.HandleCallbackParam;
import com.sunny.job.core.biz.model.RegistryParam;
import com.sunny.job.core.biz.model.ReturnT;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Service
public class AdminBizImpl implements AdminBiz {


    @Override
    public ReturnT<String> callback(List<HandleCallbackParam> callbackParamList) {
        return JobCompleteHelper.getInstance().callback(callbackParamList);
    }

    @Override
    public ReturnT<String> registry(RegistryParam registryParam) {
        return JobRegistryHelper.getInstance().registry(registryParam);
    }

    @Override
    public ReturnT<String> registryRemove(RegistryParam registryParam) {
        return JobRegistryHelper.getInstance().registryRemove(registryParam);
    }

}
