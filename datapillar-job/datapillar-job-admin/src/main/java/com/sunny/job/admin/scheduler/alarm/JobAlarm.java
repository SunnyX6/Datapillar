package com.sunny.job.admin.scheduler.alarm;

import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobLog;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public interface JobAlarm {

    /**
     * job alarm
     *
     * @param info
     * @param jobLog
     * @return
     */
    public boolean doAlarm(DatapillarJobInfo info, DatapillarJobLog jobLog);

}
