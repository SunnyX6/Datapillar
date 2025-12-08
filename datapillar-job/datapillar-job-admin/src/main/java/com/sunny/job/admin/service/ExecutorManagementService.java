package com.sunny.job.admin.service;

import com.sunny.job.admin.dto.ExecutorManagementDTO;
import com.sunny.job.core.biz.model.ReturnT;

import java.util.List;

/**
 * 执行器运维管理服务
 *
 * @author sunny
 */
public interface ExecutorManagementService {

    /**
     * 获取所有执行器列表及其在线状态
     *
     * @return 执行器运维信息列表
     */
    ReturnT<List<ExecutorManagementDTO>> listExecutors();

    /**
     * 获取指定执行器的详细信息
     *
     * @param groupId 执行器组ID
     * @return 执行器运维信息
     */
    ReturnT<ExecutorManagementDTO> getExecutorDetail(int groupId);
}
