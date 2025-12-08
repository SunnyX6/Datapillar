package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Mapper
public interface DatapillarJobRegistryMapper {

    public List<Integer> findDead(@Param("timeout") int timeout,
                                  @Param("nowTime") Date nowTime);

    public int removeDead(@Param("ids") List<Integer> ids);

    public List<DatapillarJobRegistry> findAll(@Param("timeout") int timeout,
                                        @Param("nowTime") Date nowTime);

    public int registrySaveOrUpdate(@Param("registryGroup") String registryGroup,
                            @Param("registryKey") String registryKey,
                            @Param("registryValue") String registryValue,
                            @Param("updateTime") Date updateTime);

    /*public int registryUpdate(@Param("registryGroup") String registryGroup,
                              @Param("registryKey") String registryKey,
                              @Param("registryValue") String registryValue,
                              @Param("updateTime") Date updateTime);

    public int registrySave(@Param("registryGroup") String registryGroup,
                            @Param("registryKey") String registryKey,
                            @Param("registryValue") String registryValue,
                            @Param("updateTime") Date updateTime);*/

    public int registryDelete(@Param("registryGroup") String registryGroup,
                          @Param("registryKey") String registryKey,
                          @Param("registryValue") String registryValue);

    /**
     * 根据执行器地址查询注册信息（包含负载指标）
     *
     * @param registryValue 执行器地址
     * @return 执行器注册信息
     */
    public DatapillarJobRegistry findByRegistryValue(@Param("registryValue") String registryValue);

    /**
     * 更新执行器负载信息
     *
     * @param registryValue 执行器地址
     * @param cpuUsage CPU使用率
     * @param memoryUsage 内存使用率
     * @param runningTasks 运行中的任务数
     * @param loadScore 负载评分
     * @param updateTime 更新时间
     * @return 影响行数
     */
    public int updateLoadMetrics(@Param("registryValue") String registryValue,
                                  @Param("cpuUsage") Double cpuUsage,
                                  @Param("memoryUsage") Double memoryUsage,
                                  @Param("runningTasks") Integer runningTasks,
                                  @Param("loadScore") Double loadScore,
                                  @Param("updateTime") Date updateTime);

}
