package com.sunny.job.core.biz;

import com.sunny.job.core.biz.model.*;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public interface ExecutorBiz {

    /**
     * beat
     * @return
     */
    public ReturnT<String> beat();

    /**
     * idle beat
     *
     * @param idleBeatParam
     * @return
     */
    public ReturnT<String> idleBeat(IdleBeatParam idleBeatParam);

    /**
     * run
     * @param triggerParam
     * @return
     */
    public ReturnT<String> run(TriggerParam triggerParam);

    /**
     * kill
     * @param killParam
     * @return
     */
    public ReturnT<String> kill(KillParam killParam);

    /**
     * log
     * @param logParam
     * @return
     */
    public ReturnT<LogResult> log(LogParam logParam);

    /**
     * debug run (同步执行，用于IDE调试)
     * @param triggerParam
     * @return
     */
    public ReturnT<LogResult> debugRun(TriggerParam triggerParam);

}
