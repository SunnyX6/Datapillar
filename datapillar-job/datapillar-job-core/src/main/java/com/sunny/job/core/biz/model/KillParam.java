package com.sunny.job.core.biz.model;

import java.io.Serializable;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class KillParam implements Serializable {
    private static final long serialVersionUID = 42L;

    public KillParam() {
    }
    public KillParam(int jobId) {
        this.jobId = jobId;
    }

    private int jobId;


    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

}