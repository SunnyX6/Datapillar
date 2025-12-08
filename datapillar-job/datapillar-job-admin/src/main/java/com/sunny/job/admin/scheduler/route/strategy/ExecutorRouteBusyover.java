package com.sunny.job.admin.scheduler.route.strategy;

import com.sunny.job.admin.scheduler.scheduler.DatapillarJobScheduler;
import com.sunny.job.admin.scheduler.route.ExecutorRouter;
import com.sunny.job.admin.util.I18nUtil;
import com.sunny.job.core.biz.ExecutorBiz;
import com.sunny.job.core.biz.model.IdleBeatParam;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.biz.model.TriggerParam;

import java.util.List;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class ExecutorRouteBusyover extends ExecutorRouter {

    @Override
    public ReturnT<String> route(TriggerParam triggerParam, List<String> addressList) {
        StringBuffer idleBeatResultSB = new StringBuffer();
        for (String address : addressList) {
            // beat
            ReturnT<String> idleBeatResult = null;
            try {
                ExecutorBiz executorBiz = DatapillarJobScheduler.getExecutorBiz(address);
                idleBeatResult = executorBiz.idleBeat(new IdleBeatParam(triggerParam.getJobId()));
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                idleBeatResult = ReturnT.ofFail( ""+e );
            }
            idleBeatResultSB.append( (idleBeatResultSB.length()>0)?"<br><br>":"")
                    .append(I18nUtil.getString("jobconf_idleBeat") + "：")
                    .append("<br>address：").append(address)
                    .append("<br>code：").append(idleBeatResult.getCode())
                    .append("<br>msg：").append(idleBeatResult.getMsg());

            // beat success
            if (idleBeatResult.isSuccess()) {
                idleBeatResult.setMsg(idleBeatResultSB.toString());
                idleBeatResult.setContent(address);
                return idleBeatResult;
            }
        }

        return ReturnT.ofFail( idleBeatResultSB.toString());
    }

}
