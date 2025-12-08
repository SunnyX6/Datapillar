package com.sunny.job.admin.scheduler.route.strategy;

import com.sunny.job.admin.scheduler.route.ExecutorRouter;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.biz.model.TriggerParam;

import java.util.List;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class ExecutorRouteFirst extends ExecutorRouter {

    @Override
    public ReturnT<String> route(TriggerParam triggerParam, List<String> addressList){
        return ReturnT.ofSuccess(addressList.get(0));
    }

}
