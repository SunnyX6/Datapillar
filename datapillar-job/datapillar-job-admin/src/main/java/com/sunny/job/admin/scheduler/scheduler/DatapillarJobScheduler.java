package com.sunny.job.admin.scheduler.scheduler;

import com.sunny.job.admin.scheduler.conf.DatapillarJobAdminConfig;
import com.sunny.job.admin.scheduler.thread.*;
import com.sunny.job.admin.util.I18nUtil;
import com.sunny.job.core.biz.ExecutorBiz;
import com.sunny.job.core.biz.client.ExecutorBizClient;
import com.sunny.job.core.enums.ExecutorBlockStrategyEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */

public class DatapillarJobScheduler  {
    private static final Logger logger = LoggerFactory.getLogger(DatapillarJobScheduler.class);


    public void init() throws Exception {
        // init i18n
        initI18n();

        // admin trigger pool start
        JobTriggerPoolHelper.toStart();

        // admin registry monitor run
        JobRegistryHelper.getInstance().start();

        // admin fail-monitor run
        JobFailMonitorHelper.getInstance().start();

        // admin lose-monitor run ( depend on JobTriggerPoolHelper )
        JobCompleteHelper.getInstance().start();

        // admin log report start
        JobLogReportHelper.getInstance().start();

        // start-schedule  ( depend on JobTriggerPoolHelper )
        // DISABLED: Native CRON schedule replaced by DAG workflow
        // JobScheduleHelper.getInstance().start();

        logger.info(">>>>>>>>> init datapillar-job admin success.");
    }

    
    public void destroy() throws Exception {

        // stop-schedule
        // DISABLED: Native CRON schedule replaced by DAG workflow
        // JobScheduleHelper.getInstance().toStop();

        // admin log report stop
        JobLogReportHelper.getInstance().toStop();

        // admin lose-monitor stop
        JobCompleteHelper.getInstance().toStop();

        // admin fail-monitor stop
        JobFailMonitorHelper.getInstance().toStop();

        // admin registry stop
        JobRegistryHelper.getInstance().toStop();

        // admin trigger pool stop
        JobTriggerPoolHelper.toStop();

    }

    // ---------------------- I18n ----------------------

    private void initI18n(){
        for (ExecutorBlockStrategyEnum item:ExecutorBlockStrategyEnum.values()) {
            item.setTitle(I18nUtil.getString("jobconf_block_".concat(item.name())));
        }
    }

    // ---------------------- executor-client ----------------------
    private static ConcurrentMap<String, ExecutorBiz> executorBizRepository = new ConcurrentHashMap<String, ExecutorBiz>();
    public static ExecutorBiz getExecutorBiz(String address) throws Exception {
        // valid
        if (address==null || address.trim().length()==0) {
            return null;
        }

        // load-cache
        address = address.trim();
        ExecutorBiz executorBiz = executorBizRepository.get(address);
        if (executorBiz != null) {
            return executorBiz;
        }

        // set-cache
        executorBiz = new ExecutorBizClient(address,
                DatapillarJobAdminConfig.getAdminConfig().getAccessToken(),
                DatapillarJobAdminConfig.getAdminConfig().getTimeout());

        executorBizRepository.put(address, executorBiz);
        return executorBiz;
    }

}
