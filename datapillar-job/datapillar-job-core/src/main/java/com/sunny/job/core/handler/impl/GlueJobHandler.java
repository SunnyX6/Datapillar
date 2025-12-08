package com.sunny.job.core.handler.impl;

import com.sunny.job.core.context.DatapillarJobHelper;
import com.sunny.job.core.handler.IJobHandler;

/**
 * glue job handler
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class GlueJobHandler extends IJobHandler {

	private long glueUpdatetime;
	private IJobHandler jobHandler;
	public GlueJobHandler(IJobHandler jobHandler, long glueUpdatetime) {
		this.jobHandler = jobHandler;
		this.glueUpdatetime = glueUpdatetime;
	}
	public long getGlueUpdatetime() {
		return glueUpdatetime;
	}

	@Override
	public void execute() throws Exception {
		DatapillarJobHelper.log("----------- glue.version:"+ glueUpdatetime +" -----------");
		jobHandler.execute();
	}

	@Override
	public void init() throws Exception {
		this.jobHandler.init();
	}

	@Override
	public void destroy() throws Exception {
		this.jobHandler.destroy();
	}
}
