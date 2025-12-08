package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


/**
 * job info
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Mapper
public interface DatapillarJobInfoMapper {

	public List<DatapillarJobInfo> pageList(@Param("offset") int offset,
									 @Param("pagesize") int pagesize,
									 @Param("jobGroup") int jobGroup,
									 @Param("triggerStatus") int triggerStatus,
									 @Param("jobDesc") String jobDesc,
									 @Param("executorHandler") String executorHandler,
									 @Param("author") String author);
	public int pageListCount(@Param("offset") int offset,
							 @Param("pagesize") int pagesize,
							 @Param("jobGroup") int jobGroup,
							 @Param("triggerStatus") int triggerStatus,
							 @Param("jobDesc") String jobDesc,
							 @Param("executorHandler") String executorHandler,
							 @Param("author") String author);
	
	public int save(DatapillarJobInfo info);

	public DatapillarJobInfo loadById(@Param("id") int id);
	
	public int update(DatapillarJobInfo datapillarJobInfo);
	
	public int delete(@Param("id") long id);

	public List<DatapillarJobInfo> getJobsByGroup(@Param("jobGroup") int jobGroup);

	public int findAllCount();

	/**
	 * find schedule job, limit "trigger_status = 1"
	 *
	 * @param maxNextTime
	 * @param pagesize
	 * @return
	 */
	public List<DatapillarJobInfo> scheduleJobQuery(@Param("maxNextTime") long maxNextTime, @Param("pagesize") int pagesize );

	/**
	 * update schedule job
	 *
	 * 	1、can only update "trigger_status = 1", Avoid stopping tasks from being opened
	 * 	2、valid "triggerStatus gte 0", filter illegal state
	 *
	 * @param datapillarJobInfo
	 * @return
	 */
	public int scheduleUpdate(DatapillarJobInfo datapillarJobInfo);

	/**
	 * Update workflow_id for a job
	 *
	 * @param jobId
	 * @param workflowId
	 * @return
	 */
	public int updateWorkflowId(@Param("jobId") int jobId, @Param("workflowId") long workflowId);

	/**
	 * Find all jobs by workflow_id
	 *
	 * @param workflowId
	 * @return
	 */
	public List<DatapillarJobInfo> findByWorkflowId(@Param("workflowId") long workflowId);

	/**
	 * Batch load jobs by ids
	 *
	 * @param ids
	 * @return
	 */
	public List<DatapillarJobInfo> loadByIds(@Param("ids") List<Integer> ids);

	/**
	 * 批量插入job
	 * @param jobInfoList
	 * @return
	 */
	public int batchInsert(@Param("jobInfoList") List<DatapillarJobInfo> jobInfoList);

	// ==================== 任务状态管理相关方法（从DatapillarJobTaskStateMapper迁移） ====================

	/**
	 * 根据工作流ID和任务ID加载任务信息（含状态）
	 */
	public DatapillarJobInfo loadByWorkflowAndJob(@Param("workflowId") long workflowId,
											@Param("jobId") int jobId);

	/**
	 * 根据工作流ID批量加载任务（指定jobIds）
	 */
	public List<DatapillarJobInfo> loadByWorkflowAndJobs(@Param("workflowId") long workflowId,
												   @Param("jobIds") List<Integer> jobIds);

	/**
	 * 使用行锁加载任务（FOR UPDATE）
	 */
	public DatapillarJobInfo loadByWorkflowAndJobForUpdate(@Param("workflowId") long workflowId,
													 @Param("jobId") int jobId);

	/**
	 * 更新任务状态
	 */
	public int updateStatus(DatapillarJobInfo datapillarJobInfo);

	/**
	 * 使用CAS方式更新状态（Compare-And-Set）
	 * 返回更新行数，如果返回0表示CAS失败
	 */
	public int updateStatusWithCAS(@Param("workflowId") long workflowId,
								   @Param("jobId") int jobId,
								   @Param("oldStatus") String oldStatus,
								   @Param("newStatus") String newStatus,
								   @Param("startTime") java.util.Date startTime);

	/**
	 * 原子性添加依赖完成标记
	 * 使用数据库JSON函数，避免并发丢失更新
	 */
	public int addDependencyCompletedAtomic(@Param("workflowId") long workflowId,
											@Param("jobId") int jobId,
											@Param("dependencyJobId") int dependencyJobId);

	/**
	 * 根据工作流ID查找等待状态的任务
	 */
	public List<DatapillarJobInfo> findPendingTasks(@Param("workflowId") long workflowId);

	/**
	 * 根据工作流ID和状态查找任务
	 */
	public List<DatapillarJobInfo> findByWorkflowIdAndStatus(@Param("workflowId") long workflowId,
													   @Param("status") String status);

	/**
	 * 批量更新任务状态
	 */
	public int batchUpdateStatus(@Param("workflowId") long workflowId,
								  @Param("jobIds") List<Integer> jobIds,
								  @Param("status") String status);

	/**
	 * 重置工作流所有任务状态为PENDING（用于重跑）
	 */
	public int resetWorkflowTaskStates(@Param("workflowId") long workflowId);

	/**
	 * 批量初始化任务状态（只初始化新增节点，不影响已有数据）
	 */
	public int batchInitStatusByJobIds(@Param("workflowId") long workflowId,
										@Param("jobIds") List<Integer> jobIds);

}
