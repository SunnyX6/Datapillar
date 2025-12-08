package com.sunny.job.admin.scheduler.thread;

import com.sunny.job.admin.model.DatapillarJobGroup;
import com.sunny.job.admin.model.DatapillarJobRegistry;
import com.sunny.job.admin.scheduler.conf.DatapillarJobAdminConfig;
import com.sunny.job.core.biz.model.RegistryParam;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.enums.RegistryConfig;
import com.sunny.job.admin.util.StringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * job registry instance
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class JobRegistryHelper {
	private static Logger logger = LoggerFactory.getLogger(JobRegistryHelper.class);

	private static JobRegistryHelper instance = new JobRegistryHelper();
	public static JobRegistryHelper getInstance(){
		return instance;
	}

	private ThreadPoolExecutor registryOrRemoveThreadPool = null;
	private Thread registryMonitorThread;
	private volatile boolean toStop = false;

	public void start(){

		// for registry or remove
		registryOrRemoveThreadPool = new ThreadPoolExecutor(
				2,
				10,
				30L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(2000),
				new ThreadFactory() {
					@Override
					public Thread newThread(Runnable r) {
						return new Thread(r, "datapillar-job, admin JobRegistryMonitorHelper-registryOrRemoveThreadPool-" + r.hashCode());
					}
				},
				new RejectedExecutionHandler() {
					@Override
					public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
						r.run();
						logger.warn(">>>>>>>>>>> datapillar-job, registry or remove too fast, match threadpool rejected handler(run now).");
					}
				});

		// for monitor
		registryMonitorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!toStop) {
					try {
						// auto registry group
						List<DatapillarJobGroup> groupList = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobGroupMapper().findByAddressType(0);
						if (groupList!=null && !groupList.isEmpty()) {

							// remove dead address (admin/executor)
							List<Integer> ids = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobRegistryMapper().findDead(RegistryConfig.DEAD_TIMEOUT, new Date());
							if (ids!=null && ids.size()>0) {
								DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobRegistryMapper().removeDead(ids);
							}

							// fresh online address (admin/executor)
							HashMap<String, List<String>> appAddressMap = new HashMap<String, List<String>>();
							List<DatapillarJobRegistry> list = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobRegistryMapper().findAll(RegistryConfig.DEAD_TIMEOUT, new Date());
							if (list != null) {
								for (DatapillarJobRegistry item: list) {
									if (RegistryConfig.RegistType.EXECUTOR.name().equals(item.getRegistryGroup())) {
										String appname = item.getRegistryKey();
										List<String> registryList = appAddressMap.get(appname);
										if (registryList == null) {
											registryList = new ArrayList<String>();
										}

										if (!registryList.contains(item.getRegistryValue())) {
											registryList.add(item.getRegistryValue());
										}
										appAddressMap.put(appname, registryList);
									}
								}
							}

							// fresh group address
							for (DatapillarJobGroup group: groupList) {
								List<String> registryList = appAddressMap.get(group.getAppname());
								String addressListStr = null;
								if (registryList!=null && !registryList.isEmpty()) {
									Collections.sort(registryList);
									StringBuilder addressListSB = new StringBuilder();
									for (String item:registryList) {
										addressListSB.append(item).append(",");
									}
									addressListStr = addressListSB.toString();
									addressListStr = addressListStr.substring(0, addressListStr.length()-1);
								}
								group.setAddressList(addressListStr);
								group.setUpdateTime(new Date());

								DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobGroupMapper().update(group);
							}
						}
					} catch (Throwable e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> datapillar-job, job registry monitor thread error:{}", e);
						}
					}
					try {
						TimeUnit.SECONDS.sleep(RegistryConfig.BEAT_TIMEOUT);
					} catch (Throwable e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> datapillar-job, job registry monitor thread error:{}", e);
						}
					}
				}
				logger.info(">>>>>>>>>>> datapillar-job, job registry monitor thread stop");
			}
		});
		registryMonitorThread.setDaemon(true);
		registryMonitorThread.setName("datapillar-job, admin JobRegistryMonitorHelper-registryMonitorThread");
		registryMonitorThread.start();
	}

	public void toStop(){
		toStop = true;

		// stop registryOrRemoveThreadPool
		registryOrRemoveThreadPool.shutdownNow();

		// stop monitor (interrupt and wait)
		registryMonitorThread.interrupt();
		try {
			registryMonitorThread.join();
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}
	}


	// ---------------------- helper ----------------------

	public ReturnT<String> registry(RegistryParam registryParam) {

		// valid
		if (StringTool.isBlank(registryParam.getRegistryGroup())
				|| StringTool.isBlank(registryParam.getRegistryKey())
				|| StringTool.isBlank(registryParam.getRegistryValue())) {
			return ReturnT.ofFail("Illegal Argument.");
		}

		logger.info(">>>>>>>>>>> datapillar-job registry begin, registryParam:{}", registryParam);

		// async execute
		registryOrRemoveThreadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					// 0-fail; 1-save suc; 2-update suc;
					int ret = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobRegistryMapper().registrySaveOrUpdate(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue(), new Date());
					logger.info(">>>>>>>>>>> datapillar-job registry result, ret:{}, registryParam:{}", ret, registryParam);

					if (ret == 1 || ret == 2) {
						// fresh (add or update) - 确保 executor 组存在
						freshGroupRegistryInfo(registryParam);
					} else {
						logger.warn(">>>>>>>>>>> datapillar-job registry unexpected ret value:{}", ret);
					}
				} catch (Exception e) {
					logger.error(">>>>>>>>>>> datapillar-job registry error", e);
				}
				/*int ret = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobRegistryDao().registryUpdate(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue(), new Date());
				if (ret < 1) {
					DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobRegistryDao().registrySave(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue(), new Date());

					// fresh
					freshGroupRegistryInfo(registryParam);
				}*/
			}
		});

		return ReturnT.ofSuccess();
	}

	public ReturnT<String> registryRemove(RegistryParam registryParam) {

		// valid
		if (StringTool.isBlank(registryParam.getRegistryGroup())
				|| StringTool.isBlank(registryParam.getRegistryKey())
				|| StringTool.isBlank(registryParam.getRegistryValue())) {
			return ReturnT.ofFail("Illegal Argument.");
		}

		// async execute
		registryOrRemoveThreadPool.execute(new Runnable() {
			@Override
			public void run() {
				int ret = DatapillarJobAdminConfig.getAdminConfig().getDatapillarJobRegistryMapper().registryDelete(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue());
				if (ret > 0) {
					// fresh (delete)
					freshGroupRegistryInfo(registryParam);
				}
			}
		});

		return ReturnT.ofSuccess();
	}

	private void freshGroupRegistryInfo(RegistryParam registryParam){
		try {
			logger.info(">>>>>>>>>>> datapillar-job freshGroupRegistryInfo called, registryParam:{}", registryParam);

			// 自动创建执行器组（如果不存在）
			// 只处理 EXECUTOR 类型的注册
			if (RegistryConfig.RegistType.EXECUTOR.name().equals(registryParam.getRegistryGroup())) {
				String appName = registryParam.getRegistryKey();
				logger.info(">>>>>>>>>>> datapillar-job checking executor group for appName:{}", appName);

				// 检查执行器组是否已存在
				DatapillarJobGroup existingGroup = DatapillarJobAdminConfig.getAdminConfig()
					.getDatapillarJobGroupMapper()
					.loadByAppName(appName);

				logger.info(">>>>>>>>>>> datapillar-job existing group check result, appName:{}, existingGroup:{}", appName, existingGroup);

				// 如果不存在，自动创建
				if (existingGroup == null) {
					logger.info(">>>>>>>>>>> datapillar-job creating new executor group, appName:{}", appName);

					// title 字段最多12个字符,需要截断
					String title = appName.length() > 12 ? appName.substring(0, 12) : appName;

					DatapillarJobGroup newGroup = new DatapillarJobGroup();
					newGroup.setAppname(appName);
					newGroup.setTitle(title);
					newGroup.setAddressType(0); // 0=自动注册
					newGroup.setAddressList(null);
					newGroup.setUpdateTime(new Date());

					int saveResult = DatapillarJobAdminConfig.getAdminConfig()
						.getDatapillarJobGroupMapper()
						.save(newGroup);

					logger.info(">>>>>>>>>>> datapillar-job auto registry executor group success, appName:{}, saveResult:{}, groupId:{}", appName, saveResult, newGroup.getId());
				} else {
					logger.info(">>>>>>>>>>> datapillar-job executor group already exists, appName:{}, groupId:{}", appName, existingGroup.getId());
				}
			} else {
				logger.info(">>>>>>>>>>> datapillar-job skip non-executor registry, registryGroup:{}", registryParam.getRegistryGroup());
			}
		} catch (Exception e) {
			logger.error(">>>>>>>>>>> datapillar-job freshGroupRegistryInfo error, registryParam:" + registryParam, e);
		}
	}


}
