package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobLogReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * job log
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Mapper
public interface DatapillarJobLogReportMapper {

	public int save(DatapillarJobLogReport datapillarJobLogReport);

	public int update(DatapillarJobLogReport datapillarJobLogReport);

	public List<DatapillarJobLogReport> queryLogReport(@Param("triggerDayFrom") Date triggerDayFrom,
												@Param("triggerDayTo") Date triggerDayTo);

	public DatapillarJobLogReport queryLogReportTotal();

}
