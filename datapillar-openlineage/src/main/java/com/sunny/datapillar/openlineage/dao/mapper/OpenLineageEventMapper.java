package com.sunny.datapillar.openlineage.dao.mapper;

import com.sunny.datapillar.openlineage.model.AsyncBatchRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskAttemptRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskRecord;
import com.sunny.datapillar.openlineage.model.LineageEventRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OpenLineage 事件 Mapper。
 */
@Mapper
public interface OpenLineageEventMapper {

    int insertLineageEvent(LineageEventRecord record);

    int upsertAsyncTask(AsyncTaskRecord record);

    Long selectLastInsertId();

    int claimTaskForPush(@Param("id") long id,
                         @Param("claimToken") String claimToken,
                         @Param("claimUntil") LocalDateTime claimUntil);

    List<Long> selectRecoverableTaskIdsForUpdate(@Param("taskType") String taskType,
                                                 @Param("limit") int limit);

    int markClaimedTasksByIds(@Param("ids") List<Long> ids,
                              @Param("claimToken") String claimToken,
                              @Param("claimUntil") LocalDateTime claimUntil);

    List<AsyncTaskRecord> selectTasksByIds(@Param("ids") List<Long> ids);

    AsyncTaskRecord selectTaskById(@Param("id") long id);

    int insertTaskAttempt(AsyncTaskAttemptRecord record);

    int updateTaskAttempt(@Param("id") long id,
                          @Param("status") String status,
                          @Param("finishedAt") LocalDateTime finishedAt,
                          @Param("latencyMs") Long latencyMs,
                          @Param("errorType") String errorType,
                          @Param("errorMessage") String errorMessage);

    int markTaskSucceeded(@Param("id") long id, @Param("claimToken") String claimToken);

    int markTaskFailed(@Param("id") long id,
                       @Param("claimToken") String claimToken,
                       @Param("lastError") String lastError,
                       @Param("nextRunAt") LocalDateTime nextRunAt,
                       @Param("dead") boolean dead);

    int insertBatch(AsyncBatchRecord record);

    int updateBatch(@Param("batchNo") String batchNo,
                    @Param("successCount") int successCount,
                    @Param("failedCount") int failedCount,
                    @Param("status") String status,
                    @Param("finishedAt") LocalDateTime finishedAt);
}
