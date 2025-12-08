package com.sunny.job.admin.scheduler.route.strategy;

import com.sunny.job.admin.scheduler.route.ExecutorRouter;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.biz.model.TriggerParam;

import java.util.List;
import java.util.Random;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class ExecutorRouteRandom extends ExecutorRouter {

    private static Random localRandom = new Random();

    @Override
    public ReturnT<String> route(TriggerParam triggerParam, List<String> addressList) {
        String address = addressList.get(localRandom.nextInt(addressList.size()));
        return ReturnT.ofSuccess(address);
    }

}
