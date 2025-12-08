package com.sunny.job.admin.scheduler.thread;

import com.sunny.job.admin.scheduler.conf.DatapillarJobAdminConfig;
import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobLog;
import com.sunny.job.admin.model.DatapillarJobWorkflow;
import com.sunny.job.admin.scheduler.trigger.TriggerTypeEnum;
import com.sunny.job.admin.util.I18nUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * job monitor instance
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class JobFailMonitorHelper {
	private static Logger logger = LoggerFactory.getLogger(JobFailMonitorHelper.class);
	
	private static JobFailMonitorHelper instance = new JobFailMonitorHelper();
	public static JobFailMonitorHelper getInstance(){
		return instance;
	}

	// ---------------------- monitor ----------------------

	private Thread monitorThread;
	private volatile boolean toStop = false;
	public void start(){
		monitorThread = new Thread(new Runnable() {

			@Override
			public void run() {

				// monitor
				while (!toStop) {
					try {

						List<Long> failLogIds = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobLogMapper().findFailJobLogIds(1000);
						if (failLogIds!=null && !failLogIds.isEmpty()) {
							// 优化: 批量加载日志,避免N+1查询
							List<DatapillarJobLog> failLogs = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobLogMapper().loadByIds(failLogIds);

							// 提取所有jobId并批量查询JobInfo
							java.util.Set<Integer> jobIds = new java.util.HashSet<>();
							for (DatapillarJobLog log : failLogs) {
								jobIds.add(log.getJobId());
							}
							List<DatapillarJobInfo> jobInfos = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobInfoMapper().loadByIds(new java.util.ArrayList<>(jobIds));

							// 构建jobId -> JobInfo映射
							java.util.Map<Integer, DatapillarJobInfo> jobInfoMap = new java.util.HashMap<>();
							for (DatapillarJobInfo info : jobInfos) {
								jobInfoMap.put(info.getId(), info);
							}

							// 处理每个失败日志
							for (DatapillarJobLog log : failLogs) {

								// lock log
								int lockRet = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobLogMapper().updateAlarmStatus(log.getId(), 0, -1);
								if (lockRet < 1) {
									continue;
								}
								DatapillarJobInfo info = jobInfoMap.get(log.getJobId());

								// 1、fail retry monitor
								if (log.getExecutorFailRetryCount() > 0) {
									boolean shouldRetry = true;

									if (log.getWorkflowId() != null && log.getWorkflowId() > 0) {
										DatapillarJobWorkflow workflow = DatapillarJobAdminConfig.getAdminConfig()
												.getDatapillarJobWorkflowMapper()
												.loadById(log.getWorkflowId());
										if (workflow != null &&
											Arrays.asList("STOPPED", "FAILED", "COMPLETED").contains(workflow.getStatus())) {
											shouldRetry = false;
											logger.warn("跳过重试: 工作流已结束, workflowId={}, status={}, jobId={}",
													log.getWorkflowId(), workflow.getStatus(), log.getJobId());
										}
									}

									if (shouldRetry) {
										JobTriggerPoolHelper.trigger(log.getJobId(), TriggerTypeEnum.MANUAL_SINGLE, (log.getExecutorFailRetryCount()-1), log.getExecutorShardingParam(), log.getExecutorParam(), null);
										String retryMsg = "<br><br><span style=\"color:#F39C12;\" > >>>>>>>>>>>失败重试<<<<<<<<<<< </span><br>";
										log.setTriggerMsg(log.getTriggerMsg() + retryMsg);
										DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobLogMapper().updateTriggerInfo(log);
									}
								}

								// 2、fail alarm monitor
								int newAlarmStatus = 0;		// 告警状态：0-默认、-1=锁定状态、1-无需告警、2-告警成功、3-告警失败
								if (info != null) {
									boolean alarmResult = DatapillarJobAdminConfig.getAdminConfig().getJobAlarmer().alarm(info, log);
									newAlarmStatus = alarmResult?2:3;
								} else {
									newAlarmStatus = 1;
								}

								DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobLogMapper().updateAlarmStatus(log.getId(), -1, newAlarmStatus);
							}
						}

					} catch (Throwable e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> datapillar-job, job fail monitor thread error:{}", e);
						}
					}

                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (Throwable e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }

                }

				logger.info(">>>>>>>>>>> datapillar-job, job fail monitor thread stop");

			}
		});
		monitorThread.setDaemon(true);
		monitorThread.setName("datapillar-job, admin JobFailMonitorHelper");
		monitorThread.start();
	}

	public void toStop(){
		toStop = true;
		// interrupt and wait
		monitorThread.interrupt();
		try {
			monitorThread.join();
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}
	}

}
