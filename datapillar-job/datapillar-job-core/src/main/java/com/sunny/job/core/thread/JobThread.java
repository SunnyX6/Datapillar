package com.sunny.job.core.thread;

import com.sunny.job.core.biz.model.HandleCallbackParam;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.biz.model.TriggerParam;
import com.sunny.job.core.context.DatapillarJobContext;
import com.sunny.job.core.context.DatapillarJobHelper;
import com.sunny.job.core.executor.DatapillarJobExecutor;
import com.sunny.job.core.handler.IJobHandler;
import com.sunny.job.core.log.DatapillarJobFileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;


/**
 * handler thread
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class JobThread extends Thread{
	private static Logger logger = LoggerFactory.getLogger(JobThread.class);

	private int jobId;
	private IJobHandler handler;
	private LinkedBlockingQueue<TriggerParam> triggerQueue;
	private Set<Long> triggerLogIdSet;		// avoid repeat trigger for the same TRIGGER_LOG_ID

	private volatile boolean toStop = false;
	private String stopReason;

    private boolean running = false;    // if running job
	private int idleTimes = 0;			// idle times


	public JobThread(int jobId, IJobHandler handler) {
		this.jobId = jobId;
		this.handler = handler;
		this.triggerQueue = new LinkedBlockingQueue<TriggerParam>();
		//this.triggerLogIdSet = Collections.synchronizedSet(new HashSet<Long>());
		this.triggerLogIdSet = ConcurrentHashMap.newKeySet();

		// assign job thread name
		this.setName("datapillar-job, JobThread-"+jobId+"-"+System.currentTimeMillis());
	}
	public IJobHandler getHandler() {
		return handler;
	}

    /**
     * new trigger to queue
     *
     * @param triggerParam
     * @return
     */
	public ReturnT<String> pushTriggerQueue(TriggerParam triggerParam) {
        // avoid repeat
		if (!triggerLogIdSet.add(triggerParam.getLogId())) {
			logger.info(">>>>>>>>>>> repeate trigger job, logId:{}", triggerParam.getLogId());
			return ReturnT.ofFail("repeate trigger job, logId:" + triggerParam.getLogId());
		}

		// push trigger queue
		triggerQueue.add(triggerParam);
        return ReturnT.ofSuccess();
	}

    /**
     * kill job thread
     *
     * @param stopReason
     */
	public void toStop(String stopReason) {
		/**
		 * Thread.interrupt只支持终止线程的阻塞状态(wait、join、sleep)，
		 * 在阻塞出抛出InterruptedException异常,但是并不会终止运行的线程本身；
		 * 所以需要注意，此处彻底销毁本线程，需要通过共享变量方式；
		 */
		this.toStop = true;
		this.stopReason = stopReason;
	}

    /**
     * is running job
     * @return
     */
    public boolean isRunningOrHasQueue() {
        return running || triggerQueue.size()>0;
    }

    @Override
	public void run() {

    	// init
    	try {
			handler.init();
		} catch (Throwable e) {
    		logger.error(e.getMessage(), e);
		}

		// execute
		while(!toStop){
			running = false;
			idleTimes++;

            TriggerParam triggerParam = null;
            try {
				// to check toStop signal, we need cycle, so we cannot use queue.take(), instead of poll(timeout)
				triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
				if (triggerParam!=null) {
					running = true;
					idleTimes = 0;
					triggerLogIdSet.remove(triggerParam.getLogId());

					// log filename, like "logPath/yyyy-MM-dd/9999.log"
					String logFileName = DatapillarJobFileAppender.makeLogFileName(new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
					DatapillarJobContext datapillarJobContext = new DatapillarJobContext(
							triggerParam.getJobId(),
							triggerParam.getExecutorParams(),
							logFileName,
							triggerParam.getBroadcastIndex(),
							triggerParam.getBroadcastTotal());

					// init job context
					DatapillarJobContext.setDatapillarJobContext(datapillarJobContext);

					// execute
					DatapillarJobHelper.log("<br>----------- datapillar-job job execute start -----------<br>----------- Param:" + datapillarJobContext.getJobParam());

					if (triggerParam.getExecutorTimeout() > 0) {
						// limit timeout
						Thread futureThread = null;
						try {
							FutureTask<Boolean> futureTask = new FutureTask<Boolean>(new Callable<Boolean>() {
								@Override
								public Boolean call() throws Exception {

									// init job context
									DatapillarJobContext.setDatapillarJobContext(datapillarJobContext);

									handler.execute();
									return true;
								}
							});
							futureThread = new Thread(futureTask);
							futureThread.start();

							Boolean tempResult = futureTask.get(triggerParam.getExecutorTimeout(), TimeUnit.SECONDS);
						} catch (TimeoutException e) {

							DatapillarJobHelper.log("<br>----------- datapillar-job job execute timeout");
							DatapillarJobHelper.log(e);

							// handle result
							DatapillarJobHelper.handleTimeout("job execute timeout ");
						} finally {
							futureThread.interrupt();
						}
					} else {
						// just execute
						handler.execute();
					}

					// valid execute handle data
					if (DatapillarJobContext.getDatapillarJobContext().getHandleCode() <= 0) {
						DatapillarJobHelper.handleFail("job handle result lost.");
					} else {
						String tempHandleMsg = DatapillarJobContext.getDatapillarJobContext().getHandleMsg();
						tempHandleMsg = (tempHandleMsg!=null&&tempHandleMsg.length()>50000)
								?tempHandleMsg.substring(0, 50000).concat("...")
								:tempHandleMsg;
						DatapillarJobContext.getDatapillarJobContext().setHandleMsg(tempHandleMsg);
					}
					DatapillarJobHelper.log("<br>----------- datapillar-job job execute end(finish) -----------<br>----------- Result: handleCode="
							+ DatapillarJobContext.getDatapillarJobContext().getHandleCode()
							+ ", handleMsg = "
							+ DatapillarJobContext.getDatapillarJobContext().getHandleMsg()
					);

				} else {
					if (idleTimes > 30) {
						if(triggerQueue.size() == 0) {	// avoid concurrent trigger causes jobId-lost
							DatapillarJobExecutor.removeJobThread(jobId, "excutor idle times over limit.");
						}
					}
				}
			} catch (Throwable e) {
				if (toStop) {
					DatapillarJobHelper.log("<br>----------- JobThread toStop, stopReason:" + stopReason);
				}

				// handle result
				StringWriter stringWriter = new StringWriter();
				e.printStackTrace(new PrintWriter(stringWriter));
				String errorMsg = stringWriter.toString();

				DatapillarJobHelper.handleFail(errorMsg);

				DatapillarJobHelper.log("<br>----------- JobThread Exception:" + errorMsg + "<br>----------- datapillar-job job execute end(error) -----------");
			} finally {
                if(triggerParam != null) {
                    // callback handler info
                    if (!toStop) {
                        // common
                        TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
                        		triggerParam.getLogId(),
								triggerParam.getLogDateTime(),
								DatapillarJobContext.getDatapillarJobContext().getHandleCode(),
								DatapillarJobContext.getDatapillarJobContext().getHandleMsg() )
						);
                    } else {
                        // is killed
                        TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
                        		triggerParam.getLogId(),
								triggerParam.getLogDateTime(),
								DatapillarJobContext.HANDLE_CODE_FAIL,
								stopReason + " [job running, killed]" )
						);
                    }
                }
            }
        }

		// callback trigger request in queue
		while(triggerQueue !=null && triggerQueue.size()>0){
			TriggerParam triggerParam = triggerQueue.poll();
			if (triggerParam!=null) {
				// is killed
				TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
						triggerParam.getLogId(),
						triggerParam.getLogDateTime(),
						DatapillarJobContext.HANDLE_CODE_FAIL,
						stopReason + " [job not executed, in the job queue, killed.]")
				);
			}
		}

		// destroy
		try {
			handler.destroy();
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}

		logger.info(">>>>>>>>>>> datapillar-job JobThread stoped, hashCode:{}", Thread.currentThread());
	}
}
