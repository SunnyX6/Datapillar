package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobLogGlue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * job log for glue
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Mapper
public interface DatapillarJobLogGlueMapper {
	
	public int save(DatapillarJobLogGlue datapillarJobLogGlue);
	
	public List<DatapillarJobLogGlue> findByJobId(@Param("jobId") int jobId);

	public int removeOld(@Param("jobId") int jobId, @Param("limit") int limit);

	public int deleteByJobId(@Param("jobId") int jobId);
	
}
