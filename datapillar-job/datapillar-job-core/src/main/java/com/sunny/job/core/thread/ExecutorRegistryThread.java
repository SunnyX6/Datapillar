package com.sunny.job.core.thread;

import com.sunny.job.core.biz.AdminBiz;
import com.sunny.job.core.biz.model.RegistryParam;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.enums.RegistryConfig;
import com.sunny.job.core.executor.DatapillarJobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class ExecutorRegistryThread {
    private static Logger logger = LoggerFactory.getLogger(ExecutorRegistryThread.class);

    private static ExecutorRegistryThread instance = new ExecutorRegistryThread();
    public static ExecutorRegistryThread getInstance(){
        return instance;
    }

    private Thread registryThread;
    private volatile boolean toStop = false;
    public void start(final String appname, final String address){

        // valid
        if (appname==null || appname.trim().length()==0) {
            logger.warn(">>>>>>>>>>> datapillar-job, executor registry config fail, appname is null.");
            return;
        }
        if (DatapillarJobExecutor.getAdminBizList() == null) {
            logger.warn(">>>>>>>>>>> datapillar-job, executor registry config fail, adminAddresses is null.");
            return;
        }

        registryThread = new Thread(new Runnable() {
            @Override
            public void run() {

                // registry
                while (!toStop) {
                    try {
                        RegistryParam registryParam = new RegistryParam(RegistryConfig.RegistType.EXECUTOR.name(), appname, address);
                        for (AdminBiz adminBiz: DatapillarJobExecutor.getAdminBizList()) {
                            try {
                                ReturnT<String> registryResult = adminBiz.registry(registryParam);
                                if (registryResult!=null && registryResult.isSuccess()) {
                                    registryResult = ReturnT.ofSuccess();
                                    logger.debug(">>>>>>>>>>> datapillar-job registry success, registryParam:{}, registryResult:{}", new Object[]{registryParam, registryResult});
                                    break;
                                } else {
                                    logger.info(">>>>>>>>>>> datapillar-job registry fail, registryParam:{}, registryResult:{}", new Object[]{registryParam, registryResult});
                                }
                            } catch (Throwable e) {
                                logger.info(">>>>>>>>>>> datapillar-job registry error, registryParam:{}", registryParam, e);
                            }

                        }
                    } catch (Throwable e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }

                    }

                    try {
                        if (!toStop) {
                            TimeUnit.SECONDS.sleep(RegistryConfig.BEAT_TIMEOUT);
                        }
                    } catch (Throwable e) {
                        if (!toStop) {
                            logger.warn(">>>>>>>>>>> datapillar-job, executor registry thread interrupted, error msg:{}", e.getMessage());
                        }
                    }
                }

                // registry remove
                try {
                    RegistryParam registryParam = new RegistryParam(RegistryConfig.RegistType.EXECUTOR.name(), appname, address);
                    for (AdminBiz adminBiz: DatapillarJobExecutor.getAdminBizList()) {
                        try {
                            ReturnT<String> registryResult = adminBiz.registryRemove(registryParam);
                            if (registryResult!=null && registryResult.isSuccess()) {
                                registryResult = ReturnT.ofSuccess();
                                logger.info(">>>>>>>>>>> datapillar-job registry-remove success, registryParam:{}, registryResult:{}", new Object[]{registryParam, registryResult});
                                break;
                            } else {
                                logger.info(">>>>>>>>>>> datapillar-job registry-remove fail, registryParam:{}, registryResult:{}", new Object[]{registryParam, registryResult});
                            }
                        } catch (Throwable e) {
                            if (!toStop) {
                                logger.info(">>>>>>>>>>> datapillar-job registry-remove error, registryParam:{}", registryParam, e);
                            }

                        }

                    }
                } catch (Throwable e) {
                    if (!toStop) {
                        logger.error(e.getMessage(), e);
                    }
                }
                logger.info(">>>>>>>>>>> datapillar-job, executor registry thread destroy.");

            }
        });
        registryThread.setDaemon(true);
        registryThread.setName("datapillar-job, executor ExecutorRegistryThread");
        registryThread.start();
    }

    public void toStop() {
        toStop = true;

        // interrupt and wait
        if (registryThread != null) {
            registryThread.interrupt();
            try {
                registryThread.join();
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }
        }

    }

}
