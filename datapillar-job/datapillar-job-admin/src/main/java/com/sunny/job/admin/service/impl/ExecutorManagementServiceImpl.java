package com.sunny.job.admin.service.impl;

import com.sunny.job.admin.dto.ExecutorManagementDTO;
import com.sunny.job.admin.mapper.DatapillarJobGroupMapper;
import com.sunny.job.admin.mapper.DatapillarJobRegistryMapper;
import com.sunny.job.admin.model.DatapillarJobGroup;
import com.sunny.job.admin.model.DatapillarJobRegistry;
import com.sunny.job.admin.service.ExecutorManagementService;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.enums.RegistryConfig;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 执行器运维管理服务实现
 *
 * @author sunny
 */
@Service
public class ExecutorManagementServiceImpl implements ExecutorManagementService {
    private static Logger logger = LoggerFactory.getLogger(ExecutorManagementServiceImpl.class);

    @Resource
    private DatapillarJobGroupMapper datapillarJobGroupMapper;
    @Resource
    private DatapillarJobRegistryMapper datapillarJobRegistryMapper;

    @Override
    public ReturnT<List<ExecutorManagementDTO>> listExecutors() {
        try {
            // 查询所有自动注册类型的执行器组
            List<DatapillarJobGroup> groupList = datapillarJobGroupMapper.findByAddressType(0);

            // 查询所有在线的注册记录
            List<DatapillarJobRegistry> onlineRegistryList = datapillarJobRegistryMapper.findAll(RegistryConfig.DEAD_TIMEOUT, new Date());

            // 组装DTO列表
            List<ExecutorManagementDTO> dtoList = new ArrayList<>();
            for (DatapillarJobGroup group : groupList) {
                ExecutorManagementDTO dto = new ExecutorManagementDTO();
                dto.setGroupId(group.getId());
                dto.setAppname(group.getAppname());
                dto.setTitle(group.getTitle());
                dto.setAddressType(group.getAddressType());
                dto.setUpdateTime(group.getUpdateTime());

                // 过滤出属于该执行器组的在线地址
                List<String> onlineAddresses = onlineRegistryList.stream()
                        .filter(registry -> RegistryConfig.RegistType.EXECUTOR.name().equals(registry.getRegistryGroup()))
                        .filter(registry -> group.getAppname().equals(registry.getRegistryKey()))
                        .map(DatapillarJobRegistry::getRegistryValue)
                        .collect(Collectors.toList());

                dto.setOnlineAddressList(onlineAddresses);
                dto.setOnlineCount(onlineAddresses.size());

                dtoList.add(dto);
            }

            logger.info(">>>>>>>>>>> datapillar-job list executors success, total:{}", dtoList.size());
            return ReturnT.ofSuccess(dtoList);
        } catch (Exception e) {
            logger.error(">>>>>>>>>>> datapillar-job list executors error", e);
            return ReturnT.ofFail("查询执行器列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnT<ExecutorManagementDTO> getExecutorDetail(int groupId) {
        try {
            // 查询执行器组
            DatapillarJobGroup group = datapillarJobGroupMapper.load(groupId);
            if (group == null) {
                return ReturnT.ofFail("执行器组不存在");
            }

            // 查询在线的注册记录
            List<DatapillarJobRegistry> onlineRegistryList = datapillarJobRegistryMapper.findAll(RegistryConfig.DEAD_TIMEOUT, new Date());

            // 组装DTO
            ExecutorManagementDTO dto = new ExecutorManagementDTO();
            dto.setGroupId(group.getId());
            dto.setAppname(group.getAppname());
            dto.setTitle(group.getTitle());
            dto.setAddressType(group.getAddressType());
            dto.setUpdateTime(group.getUpdateTime());

            // 过滤出属于该执行器组的在线地址
            List<String> onlineAddresses = onlineRegistryList.stream()
                    .filter(registry -> RegistryConfig.RegistType.EXECUTOR.name().equals(registry.getRegistryGroup()))
                    .filter(registry -> group.getAppname().equals(registry.getRegistryKey()))
                    .map(DatapillarJobRegistry::getRegistryValue)
                    .collect(Collectors.toList());

            dto.setOnlineAddressList(onlineAddresses);
            dto.setOnlineCount(onlineAddresses.size());

            logger.info(">>>>>>>>>>> datapillar-job get executor detail success, groupId:{}, appname:{}, onlineCount:{}",
                    groupId, group.getAppname(), onlineAddresses.size());
            return ReturnT.ofSuccess(dto);
        } catch (Exception e) {
            logger.error(">>>>>>>>>>> datapillar-job get executor detail error, groupId:{}", groupId, e);
            return ReturnT.ofFail("查询执行器详情失败: " + e.getMessage());
        }
    }
}
