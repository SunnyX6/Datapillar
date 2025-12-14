package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.entity.JobAlarmChannel;
import com.sunny.job.server.service.JobAlarmChannelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警渠道 Controller
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/alarm-channel")
public class JobAlarmChannelController {

    private final JobAlarmChannelService channelService;

    public JobAlarmChannelController(JobAlarmChannelService channelService) {
        this.channelService = channelService;
    }

    /**
     * 查询告警渠道列表
     */
    @GetMapping("/list")
    public ApiResponse<List<JobAlarmChannel>> list(@RequestParam Long namespaceId) {
        List<JobAlarmChannel> list = channelService.list(
                new LambdaQueryWrapper<JobAlarmChannel>()
                        .eq(JobAlarmChannel::getNamespaceId, namespaceId)
                        .orderByDesc(JobAlarmChannel::getUpdatedAt)
        );
        return ApiResponse.success(list);
    }

    /**
     * 根据 ID 查询
     */
    @GetMapping("/{id}")
    public ApiResponse<JobAlarmChannel> getById(@PathVariable Long id) {
        JobAlarmChannel channel = channelService.getById(id);
        if (channel == null) {
            return ApiResponse.error(404, "告警渠道不存在");
        }
        return ApiResponse.success(channel);
    }

    /**
     * 创建告警渠道
     */
    @PostMapping
    public ApiResponse<JobAlarmChannel> create(@RequestBody JobAlarmChannel channel) {
        // 检查同一命名空间下名称是否重复
        long count = channelService.count(
                new LambdaQueryWrapper<JobAlarmChannel>()
                        .eq(JobAlarmChannel::getNamespaceId, channel.getNamespaceId())
                        .eq(JobAlarmChannel::getChannelName, channel.getChannelName())
        );
        if (count > 0) {
            return ApiResponse.error(400, "告警渠道名称已存在");
        }

        channel.setChannelStatus(1);
        channelService.save(channel);
        return ApiResponse.success(channel);
    }

    /**
     * 更新告警渠道
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody JobAlarmChannel channel) {
        JobAlarmChannel existing = channelService.getById(id);
        if (existing == null) {
            return ApiResponse.error(404, "告警渠道不存在");
        }

        channel.setId(id);
        channelService.updateById(channel);
        return ApiResponse.success();
    }

    /**
     * 删除告警渠道
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        channelService.removeById(id);
        return ApiResponse.success();
    }

    /**
     * 启用告警渠道
     */
    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        JobAlarmChannel channel = new JobAlarmChannel();
        channel.setId(id);
        channel.setChannelStatus(1);
        channelService.updateById(channel);
        return ApiResponse.success();
    }

    /**
     * 禁用告警渠道
     */
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        JobAlarmChannel channel = new JobAlarmChannel();
        channel.setId(id);
        channel.setChannelStatus(0);
        channelService.updateById(channel);
        return ApiResponse.success();
    }
}
