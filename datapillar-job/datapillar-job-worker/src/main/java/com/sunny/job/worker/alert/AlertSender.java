package com.sunny.job.worker.alert;

/**
 * 告警发送器接口
 * <p>
 * 各告警渠道需实现此接口
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public interface AlertSender {

    /**
     * 发送告警
     *
     * @param channelConfig 渠道配置（JSON格式）
     * @param title         告警标题
     * @param content       告警内容
     * @return 发送结果
     */
    AlertResult send(String channelConfig, String title, String content);
}
