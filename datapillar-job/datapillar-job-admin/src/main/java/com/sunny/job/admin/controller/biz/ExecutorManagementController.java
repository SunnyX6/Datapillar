package com.sunny.job.admin.controller.biz;

import com.sunny.job.admin.dto.ExecutorManagementDTO;
import com.sunny.job.admin.service.ExecutorManagementService;
import com.sunny.job.core.biz.model.ReturnT;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 执行器运维管理Controller
 * 为运维人员提供executor在线/离线状态查询功能
 *
 * @author sunny
 */
@Controller
@RequestMapping("/executor")
public class ExecutorManagementController {
    private static Logger logger = LoggerFactory.getLogger(ExecutorManagementController.class);

    @Resource
    private ExecutorManagementService executorManagementService;

    /**
     * 获取所有执行器列表及其在线状态
     *
     * @return 执行器运维信息列表
     */
    @RequestMapping("/list/all")
    @ResponseBody
    public ReturnT<List<ExecutorManagementDTO>> listExecutors() {
        logger.info(">>>>>>>>>>> datapillar-job executor management list executors");
        return executorManagementService.listExecutors();
    }

    /**
     * 获取指定执行器的详细信息
     *
     * @param groupId 执行器组ID
     * @return 执行器运维信息
     */
    @RequestMapping("/get/detail")
    @ResponseBody
    public ReturnT<ExecutorManagementDTO> getExecutorDetail(@RequestParam("groupId") int groupId) {
        logger.info(">>>>>>>>>>> datapillar-job executor management get detail, groupId:{}", groupId);
        return executorManagementService.getExecutorDetail(groupId);
    }
}
